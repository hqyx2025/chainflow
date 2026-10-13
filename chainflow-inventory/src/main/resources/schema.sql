CREATE TABLE IF NOT EXISTS inventory_item (
    id BIGINT PRIMARY KEY AUTO_INCREMENT,
    sku VARCHAR(128) NOT NULL UNIQUE,
    available_quantity INT NOT NULL DEFAULT 0,
    reserved_quantity INT NOT NULL DEFAULT 0,
    updated_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP
);
CREATE TABLE IF NOT EXISTS inventory_reservation (
    id BIGINT PRIMARY KEY AUTO_INCREMENT,
    reservation_id VARCHAR(64) NOT NULL UNIQUE,
    order_id VARCHAR(64) NOT NULL UNIQUE,
    sku VARCHAR(128) NOT NULL,
    quantity INT NOT NULL,
    status VARCHAR(32) NOT NULL,
    created_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    KEY idx_reservation_sku (sku)
);
INSERT INTO inventory_item (sku, available_quantity, reserved_quantity)
VALUES ('SKU-DEMO-001', 100, 0)
ON DUPLICATE KEY UPDATE sku = VALUES(sku);
