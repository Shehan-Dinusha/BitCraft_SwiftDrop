-- V5__add_customer_id_to_delivery_sessions.sql
-- Add customer_id column to delivery_sessions to route notifications to the customer

ALTER TABLE delivery_sessions
    ADD COLUMN IF NOT EXISTS customer_id UUID;

CREATE INDEX IF NOT EXISTS idx_delivery_sessions_customer_id ON delivery_sessions (customer_id);

