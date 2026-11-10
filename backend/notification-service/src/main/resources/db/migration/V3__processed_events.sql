-- ─────────────────────────────────────────────────────────────────────────────
-- V3__processed_events.sql: Idempotent Consumer Deduplication Store
-- ─────────────────────────────────────────────────────────────────────────────

CREATE TABLE IF NOT EXISTS processed_events (
    event_id UUID PRIMARY KEY,
    event_type VARCHAR(100),
    processed_at TIMESTAMP WITH TIME ZONE NOT NULL DEFAULT CURRENT_TIMESTAMP
);

CREATE INDEX IF NOT EXISTS idx_processed_events_processed_at ON processed_events(processed_at DESC);
