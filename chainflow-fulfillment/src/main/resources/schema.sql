CREATE TABLE IF NOT EXISTS fulfillment_order (
    id BIGINT PRIMARY KEY AUTO_INCREMENT,
    fulfillment_id VARCHAR(64) NOT NULL UNIQUE,
    order_id VARCHAR(64) NOT NULL UNIQUE,
    carrier_code VARCHAR(64) NOT NULL,
    status VARCHAR(32) NOT NULL,
    created_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    KEY idx_fulfillment_status (status)
);
