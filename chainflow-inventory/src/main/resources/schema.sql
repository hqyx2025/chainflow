CREATE TABLE IF NOT EXISTS inventory_item (
    id BIGINT PRIMARY KEY AUTO_INCREMENT,
    sku VARCHAR(128) NOT NULL UNIQUE,
    available_quantity INT NOT NULL DEFAULT 0,
    reserved_quantity INT NOT NULL DEFAULT 0,
    updated_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
    CONSTRAINT chk_inventory_available_nonnegative CHECK (available_quantity >= 0),
    CONSTRAINT chk_inventory_reserved_nonnegative CHECK (reserved_quantity >= 0)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;
CREATE TABLE IF NOT EXISTS inventory_reservation (
    id BIGINT PRIMARY KEY AUTO_INCREMENT,
    reservation_id VARCHAR(64) NOT NULL UNIQUE,
    order_id VARCHAR(64) NOT NULL UNIQUE,
    sku VARCHAR(128) NOT NULL,
    quantity INT NOT NULL,
    status VARCHAR(32) NOT NULL,
    created_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    KEY idx_reservation_sku (sku),
    CONSTRAINT chk_reservation_quantity_positive CHECK (quantity > 0),
    CONSTRAINT chk_reservation_status CHECK (status IN ('RESERVED', 'RELEASED', 'SHIPPED'))
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;
CREATE TABLE IF NOT EXISTS inventory_receipt (
    id BIGINT PRIMARY KEY AUTO_INCREMENT,
    purchase_id BIGINT NOT NULL UNIQUE,
    sku VARCHAR(128) NOT NULL,
    quantity INT NOT NULL,
    created_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    KEY idx_receipt_sku (sku),
    CONSTRAINT chk_receipt_quantity_positive CHECK (quantity > 0)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;
INSERT INTO inventory_item (sku, available_quantity, reserved_quantity)
VALUES ('SKU-DEMO-001', 100, 0)
ON DUPLICATE KEY UPDATE sku = VALUES(sku);
