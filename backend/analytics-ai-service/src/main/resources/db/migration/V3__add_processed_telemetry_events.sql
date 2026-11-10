-- The inbox row and projection changes commit together. The primary key is the
-- concurrency guard; do not replace INSERT ON CONFLICT with an exists-then-save.
CREATE TABLE processed_telemetry_events (
    event_id UUID PRIMARY KEY,
    event_type VARCHAR(64) NOT NULL,
    order_id UUID NOT NULL,
    correlation_id UUID NOT NULL,
    occurred_at TIMESTAMP WITH TIME ZONE NOT NULL,
    payload JSONB NOT NULL,
    processed_at TIMESTAMP WITH TIME ZONE NOT NULL DEFAULT CURRENT_TIMESTAMP
);

-- Retain validated canonical events, including order.created, for the daily
-- projection backfill in Part 3. Unknown input fields are not retained.
CREATE INDEX idx_processed_telemetry_order ON processed_telemetry_events(order_id);
