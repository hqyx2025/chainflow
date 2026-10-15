-- ChainFlow V002: business quantity/status constraints for existing databases.
-- Requires MySQL 8.0.16+ (project image: 8.0.36), ALTER and CREATE ROUTINE privileges.
-- Run against the ChainFlow database after backup, with application writes paused.
-- Safe to rerun: existing named constraints are skipped; no business rows are changed.
-- DDL implicitly commits. This script does not change existing table engines/collations.
-- Do not use mysql --force: a failing precheck must stop execution before any ALTER.
-- Every count below must be 0. Resolve invalid rows before retrying; do not discard data.

SELECT 'purchase_order' AS table_name, COUNT(*) AS invalid_rows
FROM purchase_order WHERE NOT ((quantity > 0) AND (status IN ('CREATED', 'RECEIVED')));
SELECT 'inventory_item' AS table_name, COUNT(*) AS invalid_rows
FROM inventory_item WHERE NOT ((available_quantity >= 0) AND (reserved_quantity >= 0));
SELECT 'inventory_reservation' AS table_name, COUNT(*) AS invalid_rows
FROM inventory_reservation WHERE NOT ((quantity > 0) AND (status IN ('RESERVED', 'RELEASED', 'SHIPPED')));
SELECT 'inventory_receipt' AS table_name, COUNT(*) AS invalid_rows
FROM inventory_receipt WHERE NOT ((quantity > 0));
SELECT 'sales_order' AS table_name, COUNT(*) AS invalid_rows
FROM sales_order WHERE NOT ((quantity > 0) AND (payment_status IN ('PENDING_PAYMENT', 'PAID')) AND (fulfillment_status IN ('CREATED', 'READY_FOR_FULFILLMENT', 'FULFILLING')));
SELECT 'fulfillment_order' AS table_name, COUNT(*) AS invalid_rows
FROM fulfillment_order WHERE NOT ((status IN ('CREATED', 'PICKING', 'PACKED', 'SHIPPED', 'COMPLETED', 'CANCELLED')));

DROP PROCEDURE IF EXISTS chainflow_migrate_v002_business_constraints;
DELIMITER $$
CREATE PROCEDURE chainflow_migrate_v002_business_constraints()
BEGIN
    -- Validate every table before the first DDL to avoid applying part of a migration
    -- when known incompatible business data is already present.
    IF
        EXISTS (SELECT 1 FROM purchase_order WHERE NOT ((quantity > 0) AND (status IN ('CREATED', 'RECEIVED'))))
        OR
        EXISTS (SELECT 1 FROM inventory_item WHERE NOT ((available_quantity >= 0) AND (reserved_quantity >= 0)))
        OR
        EXISTS (SELECT 1 FROM inventory_reservation WHERE NOT ((quantity > 0) AND (status IN ('RESERVED', 'RELEASED', 'SHIPPED'))))
        OR
        EXISTS (SELECT 1 FROM inventory_receipt WHERE NOT ((quantity > 0)))
        OR
        EXISTS (SELECT 1 FROM sales_order WHERE NOT ((quantity > 0) AND (payment_status IN ('PENDING_PAYMENT', 'PAID')) AND (fulfillment_status IN ('CREATED', 'READY_FOR_FULFILLMENT', 'FULFILLING'))))
        OR
        EXISTS (SELECT 1 FROM fulfillment_order WHERE NOT ((status IN ('CREATED', 'PICKING', 'PACKED', 'SHIPPED', 'COMPLETED', 'CANCELLED'))))
    THEN
        SIGNAL SQLSTATE '45000'
            SET MESSAGE_TEXT = 'V002: invalid quantity/status data; review precheck counts before retrying';
    END IF;

    IF NOT EXISTS (
        SELECT 1 FROM information_schema.TABLE_CONSTRAINTS
        WHERE CONSTRAINT_SCHEMA = DATABASE() AND TABLE_NAME = 'purchase_order'
          AND CONSTRAINT_NAME = 'chk_purchase_quantity_positive' AND CONSTRAINT_TYPE = 'CHECK'
    ) THEN
        ALTER TABLE purchase_order
            ADD CONSTRAINT chk_purchase_quantity_positive CHECK (quantity > 0);
    END IF;

    IF NOT EXISTS (
        SELECT 1 FROM information_schema.TABLE_CONSTRAINTS
        WHERE CONSTRAINT_SCHEMA = DATABASE() AND TABLE_NAME = 'purchase_order'
          AND CONSTRAINT_NAME = 'chk_purchase_status' AND CONSTRAINT_TYPE = 'CHECK'
    ) THEN
        ALTER TABLE purchase_order
            ADD CONSTRAINT chk_purchase_status CHECK (status IN ('CREATED', 'RECEIVED'));
    END IF;

    IF NOT EXISTS (
        SELECT 1 FROM information_schema.TABLE_CONSTRAINTS
        WHERE CONSTRAINT_SCHEMA = DATABASE() AND TABLE_NAME = 'inventory_item'
          AND CONSTRAINT_NAME = 'chk_inventory_available_nonnegative' AND CONSTRAINT_TYPE = 'CHECK'
    ) THEN
        ALTER TABLE inventory_item
            ADD CONSTRAINT chk_inventory_available_nonnegative CHECK (available_quantity >= 0);
    END IF;

    IF NOT EXISTS (
        SELECT 1 FROM information_schema.TABLE_CONSTRAINTS
        WHERE CONSTRAINT_SCHEMA = DATABASE() AND TABLE_NAME = 'inventory_item'
          AND CONSTRAINT_NAME = 'chk_inventory_reserved_nonnegative' AND CONSTRAINT_TYPE = 'CHECK'
    ) THEN
        ALTER TABLE inventory_item
            ADD CONSTRAINT chk_inventory_reserved_nonnegative CHECK (reserved_quantity >= 0);
    END IF;

    IF NOT EXISTS (
        SELECT 1 FROM information_schema.TABLE_CONSTRAINTS
        WHERE CONSTRAINT_SCHEMA = DATABASE() AND TABLE_NAME = 'inventory_reservation'
          AND CONSTRAINT_NAME = 'chk_reservation_quantity_positive' AND CONSTRAINT_TYPE = 'CHECK'
    ) THEN
        ALTER TABLE inventory_reservation
            ADD CONSTRAINT chk_reservation_quantity_positive CHECK (quantity > 0);
    END IF;

    IF NOT EXISTS (
        SELECT 1 FROM information_schema.TABLE_CONSTRAINTS
        WHERE CONSTRAINT_SCHEMA = DATABASE() AND TABLE_NAME = 'inventory_reservation'
          AND CONSTRAINT_NAME = 'chk_reservation_status' AND CONSTRAINT_TYPE = 'CHECK'
    ) THEN
        ALTER TABLE inventory_reservation
            ADD CONSTRAINT chk_reservation_status CHECK (status IN ('RESERVED', 'RELEASED', 'SHIPPED'));
    END IF;

    IF NOT EXISTS (
        SELECT 1 FROM information_schema.TABLE_CONSTRAINTS
        WHERE CONSTRAINT_SCHEMA = DATABASE() AND TABLE_NAME = 'inventory_receipt'
          AND CONSTRAINT_NAME = 'chk_receipt_quantity_positive' AND CONSTRAINT_TYPE = 'CHECK'
    ) THEN
        ALTER TABLE inventory_receipt
            ADD CONSTRAINT chk_receipt_quantity_positive CHECK (quantity > 0);
    END IF;

    IF NOT EXISTS (
        SELECT 1 FROM information_schema.TABLE_CONSTRAINTS
        WHERE CONSTRAINT_SCHEMA = DATABASE() AND TABLE_NAME = 'sales_order'
          AND CONSTRAINT_NAME = 'chk_order_quantity_positive' AND CONSTRAINT_TYPE = 'CHECK'
    ) THEN
        ALTER TABLE sales_order
            ADD CONSTRAINT chk_order_quantity_positive CHECK (quantity > 0);
    END IF;

    IF NOT EXISTS (
        SELECT 1 FROM information_schema.TABLE_CONSTRAINTS
        WHERE CONSTRAINT_SCHEMA = DATABASE() AND TABLE_NAME = 'sales_order'
          AND CONSTRAINT_NAME = 'chk_order_payment_status' AND CONSTRAINT_TYPE = 'CHECK'
    ) THEN
        ALTER TABLE sales_order
            ADD CONSTRAINT chk_order_payment_status CHECK (payment_status IN ('PENDING_PAYMENT', 'PAID'));
    END IF;

    IF NOT EXISTS (
        SELECT 1 FROM information_schema.TABLE_CONSTRAINTS
        WHERE CONSTRAINT_SCHEMA = DATABASE() AND TABLE_NAME = 'sales_order'
          AND CONSTRAINT_NAME = 'chk_order_fulfillment_status' AND CONSTRAINT_TYPE = 'CHECK'
    ) THEN
        ALTER TABLE sales_order
            ADD CONSTRAINT chk_order_fulfillment_status CHECK (fulfillment_status IN ('CREATED', 'READY_FOR_FULFILLMENT', 'FULFILLING'));
    END IF;

    IF NOT EXISTS (
        SELECT 1 FROM information_schema.TABLE_CONSTRAINTS
        WHERE CONSTRAINT_SCHEMA = DATABASE() AND TABLE_NAME = 'fulfillment_order'
          AND CONSTRAINT_NAME = 'chk_fulfillment_status' AND CONSTRAINT_TYPE = 'CHECK'
    ) THEN
        ALTER TABLE fulfillment_order
            ADD CONSTRAINT chk_fulfillment_status CHECK (status IN ('CREATED', 'PICKING', 'PACKED', 'SHIPPED', 'COMPLETED', 'CANCELLED'));
    END IF;

END$$
DELIMITER ;
CALL chainflow_migrate_v002_business_constraints();
DROP PROCEDURE chainflow_migrate_v002_business_constraints;

SELECT TABLE_NAME, CONSTRAINT_NAME, ENFORCED
FROM information_schema.TABLE_CONSTRAINTS
WHERE CONSTRAINT_SCHEMA = DATABASE() AND CONSTRAINT_TYPE = 'CHECK'
ORDER BY TABLE_NAME, CONSTRAINT_NAME;

