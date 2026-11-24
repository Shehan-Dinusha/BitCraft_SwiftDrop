-- V8__deliver_by.sql
-- The day the customer is promised delivery, worked out from the speed, the districts and the working days
-- when the order is booked (or its speed or districts change before pickup). Older orders have none.

ALTER TABLE customer_orders ADD COLUMN IF NOT EXISTS deliver_by DATE;

COMMENT ON COLUMN customer_orders.deliver_by IS 'Promised delivery day: Same day today, Express the next working day after pickup, Standard two (same district) or three working days after pickup';
