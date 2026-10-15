CREATE TABLE IF NOT EXISTS sales_order (
    id BIGINT PRIMARY KEY AUTO_INCREMENT,
    order_id VARCHAR(64) NOT NULL UNIQUE,
    sku VARCHAR(128) NOT NULL,
    quantity INT NOT NULL,
    payment_status VARCHAR(32) NOT NULL,
    fulfillment_status VARCHAR(32) NOT NULL,
    created_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    KEY idx_order_payment (payment_status),
    KEY idx_order_fulfillment (fulfillment_status),
    CONSTRAINT chk_order_quantity_positive CHECK (quantity > 0),
    CONSTRAINT chk_order_payment_status CHECK (payment_status IN ('PENDING_PAYMENT', 'PAID')),
    CONSTRAINT chk_order_fulfillment_status CHECK (fulfillment_status IN ('CREATED', 'READY_FOR_FULFILLMENT', 'FULFILLING'))
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;
