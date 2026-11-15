-- Keep the customer of each order, so every courier assignment (automatic or manual)
-- can tell delivery-execution-service whom to notify. Rows created before this
-- migration stay NULL; their notifications fall back to "no recipient".
ALTER TABLE dispatch_assignments
    ADD COLUMN IF NOT EXISTS customer_id UUID;
