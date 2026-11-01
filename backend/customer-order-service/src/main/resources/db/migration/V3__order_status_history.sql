-- V3: status history per order, used by the internal tracking lookup and the status-sync consumer.
-- The FK stays inside this database (order_id is unique on customer_orders); no cross-service references.

CREATE TABLE IF NOT EXISTS order_status_history (
    id BIGSERIAL PRIMARY KEY,
    order_id UUID NOT NULL REFERENCES customer_orders(order_id) ON DELETE CASCADE,
    status VARCHAR(64) NOT NULL,
    internal_sub_status VARCHAR(64),
    event_id UUID,
    occurred_at TIMESTAMP WITH TIME ZONE NOT NULL,
    created_at TIMESTAMP WITH TIME ZONE NOT NULL DEFAULT CURRENT_TIMESTAMP
);

CREATE INDEX IF NOT EXISTS idx_order_status_history_order ON order_status_history(order_id, occurred_at);
CREATE UNIQUE INDEX IF NOT EXISTS uk_order_status_history_event ON order_status_history(event_id) WHERE event_id IS NOT NULL;

-- Backfill so orders created before this migration still have a history: creation, then the current status if it moved on.
INSERT INTO order_status_history (order_id, status, internal_sub_status, occurred_at)
SELECT order_id, 'CREATED', 'CREATED', created_at FROM customer_orders;

INSERT INTO order_status_history (order_id, status, internal_sub_status, occurred_at)
SELECT order_id, status, internal_queue_status, updated_at FROM customer_orders WHERE status <> 'CREATED';
