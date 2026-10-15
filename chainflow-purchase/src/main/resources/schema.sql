CREATE TABLE IF NOT EXISTS purchase_order (
    id BIGINT PRIMARY KEY AUTO_INCREMENT,
    supplier_code VARCHAR(64) NOT NULL,
    sku VARCHAR(128) NOT NULL,
    quantity INT NOT NULL,
    status VARCHAR(32) NOT NULL,
    created_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    KEY idx_purchase_supplier (supplier_code),
    KEY idx_purchase_status (status),
    CONSTRAINT chk_purchase_quantity_positive CHECK (quantity > 0),
    CONSTRAINT chk_purchase_status CHECK (status IN ('CREATED', 'RECEIVED'))
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;
