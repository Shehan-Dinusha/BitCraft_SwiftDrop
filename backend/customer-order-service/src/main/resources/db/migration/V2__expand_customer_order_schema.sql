-- V2: Expand customer orders schema for full lifecycle, pickup/drop-off, dimensions, pricing, and fee revisions
-- Matches V1 table customer_orders

ALTER TABLE customer_orders
    ADD COLUMN IF NOT EXISTS order_id UUID NOT NULL DEFAULT gen_random_uuid(),
    ADD COLUMN IF NOT EXISTS tracking_number VARCHAR(32),
    ADD COLUMN IF NOT EXISTS customer_id UUID,
    ADD COLUMN IF NOT EXISTS merchant_id UUID,
    ADD COLUMN IF NOT EXISTS order_speed VARCHAR(32) NOT NULL DEFAULT 'STANDARD',
    ADD COLUMN IF NOT EXISTS internal_queue_status VARCHAR(64) NOT NULL DEFAULT 'CREATED',
    ADD COLUMN IF NOT EXISTS pickup_address_line1 VARCHAR(255),
    ADD COLUMN IF NOT EXISTS pickup_address_line2 VARCHAR(255),
    ADD COLUMN IF NOT EXISTS pickup_city VARCHAR(128),
    ADD COLUMN IF NOT EXISTS pickup_district VARCHAR(64),
    ADD COLUMN IF NOT EXISTS pickup_postal_code VARCHAR(20),
    ADD COLUMN IF NOT EXISTS pickup_contact_name VARCHAR(128),
    ADD COLUMN IF NOT EXISTS pickup_contact_phone VARCHAR(32),
    ADD COLUMN IF NOT EXISTS delivery_address_line1 VARCHAR(255),
    ADD COLUMN IF NOT EXISTS delivery_address_line2 VARCHAR(255),
    ADD COLUMN IF NOT EXISTS delivery_city VARCHAR(128),
    ADD COLUMN IF NOT EXISTS delivery_district VARCHAR(64),
    ADD COLUMN IF NOT EXISTS delivery_postal_code VARCHAR(20),
    ADD COLUMN IF NOT EXISTS recipient_name VARCHAR(128),
    ADD COLUMN IF NOT EXISTS recipient_phone VARCHAR(32),
    ADD COLUMN IF NOT EXISTS recipient_email VARCHAR(128),
    ADD COLUMN IF NOT EXISTS length_cm NUMERIC(8, 2) NOT NULL DEFAULT 10.0,
    ADD COLUMN IF NOT EXISTS width_cm NUMERIC(8, 2) NOT NULL DEFAULT 10.0,
    ADD COLUMN IF NOT EXISTS height_cm NUMERIC(8, 2) NOT NULL DEFAULT 10.0,
    ADD COLUMN IF NOT EXISTS actual_weight_kg NUMERIC(8, 2) NOT NULL DEFAULT 1.0,
    ADD COLUMN IF NOT EXISTS chargeable_weight_kg NUMERIC(8, 2) NOT NULL DEFAULT 1.0,
    ADD COLUMN IF NOT EXISTS item_description VARCHAR(512),
    ADD COLUMN IF NOT EXISTS declared_value NUMERIC(12, 2) DEFAULT 0.00,
    ADD COLUMN IF NOT EXISTS cod_amount NUMERIC(12, 2) DEFAULT 0.00,
    ADD COLUMN IF NOT EXISTS prohibited_items_declared BOOLEAN NOT NULL DEFAULT false,
    ADD COLUMN IF NOT EXISTS pricing_rule_version_id VARCHAR(64) NOT NULL DEFAULT 'PRICING-2026-V1',
    ADD COLUMN IF NOT EXISTS estimated_fee NUMERIC(10, 2) NOT NULL DEFAULT 350.00,
    ADD COLUMN IF NOT EXISTS final_fee NUMERIC(10, 2) NOT NULL DEFAULT 350.00,
    ADD COLUMN IF NOT EXISTS currency VARCHAR(8) NOT NULL DEFAULT 'LKR',
    ADD COLUMN IF NOT EXISTS fee_revision_status VARCHAR(32) NOT NULL DEFAULT 'APPROVED',
    ADD COLUMN IF NOT EXISTS revised_fee NUMERIC(10, 2),
    ADD COLUMN IF NOT EXISTS fee_revision_reason VARCHAR(512),
    ADD COLUMN IF NOT EXISTS cancellation_reason VARCHAR(512),
    ADD COLUMN IF NOT EXISTS cancelled_at TIMESTAMP WITH TIME ZONE;

CREATE UNIQUE INDEX IF NOT EXISTS uk_customer_orders_order_id ON customer_orders(order_id);
CREATE UNIQUE INDEX IF NOT EXISTS uk_customer_orders_tracking ON customer_orders(tracking_number);
CREATE INDEX IF NOT EXISTS idx_customer_orders_cust ON customer_orders(customer_id);
CREATE INDEX IF NOT EXISTS idx_customer_orders_status ON customer_orders(status);

CREATE TABLE IF NOT EXISTS order_address (
    id BIGSERIAL PRIMARY KEY
);

CREATE TABLE IF NOT EXISTS order_item (
    id BIGSERIAL PRIMARY KEY
);

