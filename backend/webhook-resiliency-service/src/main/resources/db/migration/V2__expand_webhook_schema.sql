-- Expand webhook_endpoints schema
ALTER TABLE webhook_endpoints
    ADD COLUMN IF NOT EXISTS endpoint_id UUID NOT NULL DEFAULT gen_random_uuid(),
    ADD COLUMN IF NOT EXISTS merchant_id UUID NOT NULL DEFAULT gen_random_uuid(),
    ADD COLUMN IF NOT EXISTS target_url VARCHAR(500) NOT NULL DEFAULT 'https://example.com/webhook',
    ADD COLUMN IF NOT EXISTS secret_key VARCHAR(128) NOT NULL DEFAULT 'whsec_default_secret',
    ADD COLUMN IF NOT EXISTS subscribed_events VARCHAR(255) NOT NULL DEFAULT 'parcel.status.changed',
    ADD COLUMN IF NOT EXISTS is_active BOOLEAN NOT NULL DEFAULT TRUE,
    ADD COLUMN IF NOT EXISTS description VARCHAR(150);

CREATE UNIQUE INDEX IF NOT EXISTS uq_webhook_endpoints_endpoint_id ON webhook_endpoints(endpoint_id);
CREATE INDEX IF NOT EXISTS idx_webhook_endpoints_merchant ON webhook_endpoints(merchant_id);
CREATE INDEX IF NOT EXISTS idx_webhook_endpoints_active ON webhook_endpoints(is_active);

-- Webhook Delivery Attempts Table
CREATE TABLE IF NOT EXISTS webhook_delivery_attempts (
    id BIGSERIAL PRIMARY KEY,
    attempt_id UUID NOT NULL DEFAULT gen_random_uuid(),
    endpoint_id UUID NOT NULL,
    merchant_id UUID NOT NULL,
    event_type VARCHAR(100) NOT NULL,
    payload TEXT NOT NULL,
    http_status_code INT,
    attempt_number INT NOT NULL DEFAULT 1,
    is_success BOOLEAN NOT NULL DEFAULT FALSE,
    response_body TEXT,
    error_message VARCHAR(500),
    attempted_at TIMESTAMP WITH TIME ZONE NOT NULL DEFAULT CURRENT_TIMESTAMP
);

CREATE UNIQUE INDEX IF NOT EXISTS uq_webhook_delivery_attempts_attempt_id ON webhook_delivery_attempts(attempt_id);
CREATE INDEX IF NOT EXISTS idx_webhook_delivery_attempts_endpoint ON webhook_delivery_attempts(endpoint_id, attempted_at DESC);
CREATE INDEX IF NOT EXISTS idx_webhook_delivery_attempts_merchant ON webhook_delivery_attempts(merchant_id);

-- Dead Letter Webhook Records Table
CREATE TABLE IF NOT EXISTS dead_letter_webhook_records (
    id BIGSERIAL PRIMARY KEY,
    dlq_id UUID NOT NULL DEFAULT gen_random_uuid(),
    endpoint_id UUID NOT NULL,
    merchant_id UUID NOT NULL,
    event_type VARCHAR(100) NOT NULL,
    payload TEXT NOT NULL,
    failure_reason VARCHAR(500),
    total_attempts INT NOT NULL DEFAULT 3,
    is_replayed BOOLEAN NOT NULL DEFAULT FALSE,
    replayed_at TIMESTAMP WITH TIME ZONE,
    created_at TIMESTAMP WITH TIME ZONE NOT NULL DEFAULT CURRENT_TIMESTAMP
);

CREATE UNIQUE INDEX IF NOT EXISTS uq_dlq_webhook_records_dlq_id ON dead_letter_webhook_records(dlq_id);
CREATE INDEX IF NOT EXISTS idx_dlq_webhook_records_merchant ON dead_letter_webhook_records(merchant_id, created_at DESC);
CREATE INDEX IF NOT EXISTS idx_dlq_webhook_records_replayed ON dead_letter_webhook_records(is_replayed);
