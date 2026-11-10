-- V4: where an order came from, and the merchant's own reference for idempotent creation.
-- customer_orders.recipient_email is already nullable in the database (V2); only the JPA mapping changes.

ALTER TABLE customer_orders
    ADD COLUMN IF NOT EXISTS source VARCHAR(16) NOT NULL DEFAULT 'CUSTOMER',
    ADD COLUMN IF NOT EXISTS external_ref VARCHAR(128);

-- One order per (merchant, external reference). Customer orders have no external_ref and are not constrained.
CREATE UNIQUE INDEX IF NOT EXISTS uk_customer_orders_merchant_external_ref
    ON customer_orders(merchant_id, external_ref)
    WHERE external_ref IS NOT NULL;
