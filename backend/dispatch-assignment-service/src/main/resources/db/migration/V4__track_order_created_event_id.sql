-- Track the last order-created event handled for retry-safe duplicate delivery.
ALTER TABLE dispatch_assignments
    ADD COLUMN IF NOT EXISTS last_order_created_event_id UUID;
