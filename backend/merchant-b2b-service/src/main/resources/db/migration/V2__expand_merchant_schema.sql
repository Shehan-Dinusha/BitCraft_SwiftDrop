-- Expand merchant_accounts schema
ALTER TABLE merchant_accounts
    ADD COLUMN IF NOT EXISTS merchant_id UUID NOT NULL DEFAULT gen_random_uuid(),
    ADD COLUMN IF NOT EXISTS business_name VARCHAR(150) NOT NULL DEFAULT 'Default Merchant',
    ADD COLUMN IF NOT EXISTS contact_email VARCHAR(100),
    ADD COLUMN IF NOT EXISTS contact_phone VARCHAR(30),
    ADD COLUMN IF NOT EXISTS pickup_address VARCHAR(255),
    ADD COLUMN IF NOT EXISTS operating_district VARCHAR(50) NOT NULL DEFAULT 'COLOMBO';

CREATE UNIQUE INDEX IF NOT EXISTS uq_merchant_accounts_merchant_id ON merchant_accounts(merchant_id);
CREATE INDEX IF NOT EXISTS idx_merchant_accounts_district ON merchant_accounts(operating_district);
CREATE INDEX IF NOT EXISTS idx_merchant_accounts_status ON merchant_accounts(status);

-- Merchant API Keys Table
CREATE TABLE IF NOT EXISTS merchant_api_keys (
    id BIGSERIAL PRIMARY KEY,
    key_id UUID NOT NULL DEFAULT gen_random_uuid(),
    merchant_id UUID NOT NULL,
    key_name VARCHAR(100) NOT NULL DEFAULT 'Default API Key',
    key_prefix VARCHAR(24) NOT NULL,
    key_hash VARCHAR(64) NOT NULL,
    is_active BOOLEAN NOT NULL DEFAULT TRUE,
    expires_at TIMESTAMP WITH TIME ZONE,
    last_used_at TIMESTAMP WITH TIME ZONE,
    created_at TIMESTAMP WITH TIME ZONE NOT NULL DEFAULT CURRENT_TIMESTAMP,
    revoked_at TIMESTAMP WITH TIME ZONE
);

CREATE UNIQUE INDEX IF NOT EXISTS uq_merchant_api_keys_key_id ON merchant_api_keys(key_id);
CREATE UNIQUE INDEX IF NOT EXISTS uq_merchant_api_keys_key_hash ON merchant_api_keys(key_hash);
CREATE INDEX IF NOT EXISTS idx_merchant_api_keys_merchant_id ON merchant_api_keys(merchant_id);
CREATE INDEX IF NOT EXISTS idx_merchant_api_keys_active ON merchant_api_keys(merchant_id, is_active);

-- Bulk Ingestion Batches Table
CREATE TABLE IF NOT EXISTS bulk_ingest_batches (
    id BIGSERIAL PRIMARY KEY,
    batch_id UUID NOT NULL DEFAULT gen_random_uuid(),
    merchant_id UUID NOT NULL,
    file_name VARCHAR(255) NOT NULL,
    batch_status VARCHAR(64) NOT NULL DEFAULT 'PENDING',
    total_records INT NOT NULL DEFAULT 0,
    success_count INT NOT NULL DEFAULT 0,
    failed_count INT NOT NULL DEFAULT 0,
    error_summary TEXT,
    created_at TIMESTAMP WITH TIME ZONE NOT NULL DEFAULT CURRENT_TIMESTAMP,
    completed_at TIMESTAMP WITH TIME ZONE
);

CREATE UNIQUE INDEX IF NOT EXISTS uq_bulk_ingest_batches_batch_id ON bulk_ingest_batches(batch_id);
CREATE INDEX IF NOT EXISTS idx_bulk_ingest_batches_merchant ON bulk_ingest_batches(merchant_id, created_at DESC);
CREATE INDEX IF NOT EXISTS idx_bulk_ingest_batches_status ON bulk_ingest_batches(batch_status);

-- Bulk Ingest Batch Items Table
CREATE TABLE IF NOT EXISTS bulk_batch_items (
    id BIGSERIAL PRIMARY KEY,
    batch_id UUID NOT NULL,
    row_index INT NOT NULL,
    tracking_number VARCHAR(64),
    order_id UUID,
    recipient_name VARCHAR(100) NOT NULL,
    recipient_phone VARCHAR(30) NOT NULL,
    delivery_district VARCHAR(50) NOT NULL,
    delivery_address VARCHAR(255) NOT NULL,
    package_weight_kg NUMERIC(6,2) NOT NULL DEFAULT 1.0,
    delivery_speed VARCHAR(50) NOT NULL DEFAULT 'STANDARD',
    item_status VARCHAR(50) NOT NULL DEFAULT 'PENDING',
    error_message VARCHAR(255),
    created_at TIMESTAMP WITH TIME ZONE NOT NULL DEFAULT CURRENT_TIMESTAMP
);

CREATE INDEX IF NOT EXISTS idx_bulk_batch_items_batch ON bulk_batch_items(batch_id, row_index);
CREATE INDEX IF NOT EXISTS idx_bulk_batch_items_order ON bulk_batch_items(order_id);
CREATE INDEX IF NOT EXISTS idx_bulk_batch_items_tracking ON bulk_batch_items(tracking_number);
