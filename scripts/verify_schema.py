"""Exercise fresh schemas and V002 migration in a disposable MySQL 8 database.

Requires a running MySQL Docker container. Existing application tables and data
are never modified. Set CHAINFLOW_TEST_DB_ROOT_PASSWORD for custom local setups.
"""

import argparse
import os
from pathlib import Path
import re
import subprocess
import uuid


def main():
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument("--container", default="chainflow-mysql")
    args = parser.parse_args()
    root = Path(__file__).resolve().parents[1]
    database = "chainflow_verify_" + uuid.uuid4().hex
    assert re.fullmatch(r"chainflow_verify_[a-f0-9]{32}", database)
    password = os.environ.get("CHAINFLOW_TEST_DB_ROOT_PASSWORD", "chainflow_root")

    def sql(statement, *, expected_error=None, use_database=True):
        command = ["docker", "exec", "-i", "-e", "MYSQL_PWD=" + password,
                   args.container, "mysql", "--batch", "--skip-column-names", "-uroot"]
        if use_database:
            command.append(database)
        result = subprocess.run(command, input=statement, text=True,
                                encoding="utf-8", capture_output=True, timeout=60)
        if expected_error is not None:
            if result.returncode == 0 or f"ERROR {expected_error} " not in result.stderr:
                raise RuntimeError(f"Expected MySQL error {expected_error}: {result.stderr}")
        elif result.returncode != 0:
            raise RuntimeError(result.stderr)
        return result.stdout.strip()

    constraint_query = """SELECT TABLE_NAME, CONSTRAINT_NAME
        FROM information_schema.TABLE_CONSTRAINTS
        WHERE CONSTRAINT_SCHEMA = DATABASE() AND CONSTRAINT_TYPE = 'CHECK'
        ORDER BY TABLE_NAME, CONSTRAINT_NAME;"""
    invalid_statements = [
        "INSERT INTO purchase_order(supplier_code,sku,quantity,status) VALUES ('QA','QA',0,'CREATED');",
        "INSERT INTO purchase_order(supplier_code,sku,quantity,status) VALUES ('QA','QA',1,'INVALID');",
        "UPDATE inventory_item SET available_quantity=-1 WHERE sku='SKU-DEMO-001';",
        "UPDATE inventory_item SET reserved_quantity=-1 WHERE sku='SKU-DEMO-001';",
        "INSERT INTO inventory_reservation(reservation_id,order_id,sku,quantity,status) VALUES ('QA','QA','QA',0,'RESERVED');",
        "INSERT INTO inventory_reservation(reservation_id,order_id,sku,quantity,status) VALUES ('QA','QA','QA',1,'INVALID');",
        "INSERT INTO inventory_receipt(purchase_id,sku,quantity) VALUES (1,'QA',0);",
        "INSERT INTO sales_order(order_id,sku,quantity,payment_status,fulfillment_status) VALUES ('QA','QA',0,'PENDING_PAYMENT','CREATED');",
        "INSERT INTO sales_order(order_id,sku,quantity,payment_status,fulfillment_status) VALUES ('QA','QA',1,'INVALID','CREATED');",
        "INSERT INTO sales_order(order_id,sku,quantity,payment_status,fulfillment_status) VALUES ('QA','QA',1,'PAID','INVALID');",
        "INSERT INTO fulfillment_order(fulfillment_id,order_id,carrier_code,status) VALUES ('QA','QA','QA','INVALID');",
    ]

    sql(f"CREATE DATABASE `{database}` CHARACTER SET utf8mb4;", use_database=False)
    try:
        for module in ("purchase", "inventory", "order", "fulfillment"):
            schema = root / f"chainflow-{module}" / "src/main/resources/schema.sql"
            sql(schema.read_text(encoding="utf-8-sig"))
        constraints = sql(constraint_query).splitlines()
        if len(constraints) != 11:
            raise RuntimeError(f"Fresh schema expected 11 CHECK constraints, found {len(constraints)}")
        for statement in invalid_statements:
            sql(statement, expected_error=3819)

        # Remove constraints in this test database to model a previous release.
        for constraint in constraints:
            table, name = constraint.split("\t")
            if not re.fullmatch(r"[a-z_]+", table) or not re.fullmatch(r"[a-z_]+", name):
                raise RuntimeError("Unexpected constraint identifier")
            sql(f"ALTER TABLE `{table}` DROP CHECK `{name}`;")
        sql("INSERT INTO purchase_order(supplier_code,sku,quantity,status) VALUES ('MIGRATION-QA','QA',0,'CREATED');")
        migration = (root / "infra/mysql/migrations/V002__business_constraints.sql").read_text(encoding="utf-8-sig")
        sql(migration, expected_error=1644)
        if sql(constraint_query):
            raise RuntimeError("Dirty-data precheck applied constraints before aborting")
        if sql("SELECT COUNT(*) FROM purchase_order WHERE supplier_code='MIGRATION-QA';") != "1":
            raise RuntimeError("Precheck altered the invalid business record")
        sql("DELETE FROM purchase_order WHERE supplier_code='MIGRATION-QA';")
        sql(migration)
        sql(migration)  # Running the migration again must be safe.
        if sql(constraint_query).splitlines() != constraints:
            raise RuntimeError("Migration did not restore all named constraints")
        for statement in invalid_statements:
            sql(statement, expected_error=3819)
        print("PASS: fresh schema, 11 constraints, dirty-data precheck, migration upgrade and rerun")
    finally:
        # The name is generated above and never comes from a user or table row.
        sql(f"DROP DATABASE `{database}`;", use_database=False)


if __name__ == "__main__":
    main()
