-- V3__expand_delivery_execution_schema.sql
-- Expand delivery_sessions, state_transition_audits, tracking_breadcrumbs

ALTER TABLE delivery_sessions
    ADD COLUMN IF NOT EXISTS delivery_id UUID NOT NULL DEFAULT gen_random_uuid(),
    ADD COLUMN IF NOT EXISTS order_id UUID,
    ADD COLUMN IF NOT EXISTS tracking_number VARCHAR(32),
    ADD COLUMN IF NOT EXISTS courier_id UUID,
    ADD COLUMN IF NOT EXISTS courier_name VARCHAR(128),
    ADD COLUMN IF NOT EXISTS pickup_district VARCHAR(64),
    ADD COLUMN IF NOT EXISTS delivery_district VARCHAR(64),
    ADD COLUMN IF NOT EXISTS current_state VARCHAR(32) NOT NULL DEFAULT 'CREATED',
    ADD COLUMN IF NOT EXISTS previous_state VARCHAR(32),
    ADD COLUMN IF NOT EXISTS failed_reason VARCHAR(512),
    ADD COLUMN IF NOT EXISTS attempt_count INT NOT NULL DEFAULT 0,
    ADD COLUMN IF NOT EXISTS pickup_at TIMESTAMP WITH TIME ZONE,
    ADD COLUMN IF NOT EXISTS delivered_at TIMESTAMP WITH TIME ZONE,
    ADD COLUMN IF NOT EXISTS current_latitude NUMERIC(10, 7),
    ADD COLUMN IF NOT EXISTS current_longitude NUMERIC(10, 7);

CREATE TABLE IF NOT EXISTS state_transition_audits (
    id BIGSERIAL PRIMARY KEY,
    delivery_id UUID NOT NULL,
    order_id UUID NOT NULL,
    from_state VARCHAR(32),
    to_state VARCHAR(32) NOT NULL,
    triggered_by VARCHAR(64) NOT NULL DEFAULT 'COURIER_APP',
    actor_role VARCHAR(32) DEFAULT 'COURIER',
    notes VARCHAR(512),
    latitude NUMERIC(10, 7),
    longitude NUMERIC(10, 7),
    created_at TIMESTAMP WITH TIME ZONE NOT NULL DEFAULT CURRENT_TIMESTAMP
);

CREATE TABLE IF NOT EXISTS tracking_breadcrumbs (
    id BIGSERIAL PRIMARY KEY,
    delivery_id UUID NOT NULL,
    courier_id UUID NOT NULL,
    latitude NUMERIC(10, 7) NOT NULL,
    longitude NUMERIC(10, 7) NOT NULL,
    bearing_degrees NUMERIC(5, 2) DEFAULT 0.0,
    speed_kmh NUMERIC(5, 2) DEFAULT 0.0,
    accuracy NUMERIC(6, 2) DEFAULT 5.0,
    recorded_at TIMESTAMP WITH TIME ZONE NOT NULL DEFAULT CURRENT_TIMESTAMP
);

CREATE UNIQUE INDEX IF NOT EXISTS uk_delivery_sessions_delivery_id ON delivery_sessions (delivery_id);
CREATE INDEX IF NOT EXISTS idx_delivery_sessions_order_id ON delivery_sessions (order_id);
CREATE INDEX IF NOT EXISTS idx_delivery_sessions_tracking_number ON delivery_sessions (tracking_number);
CREATE INDEX IF NOT EXISTS idx_delivery_sessions_courier_id ON delivery_sessions (courier_id);
CREATE INDEX IF NOT EXISTS idx_delivery_sessions_current_state ON delivery_sessions (current_state);
CREATE INDEX IF NOT EXISTS idx_state_transition_audits_delivery_id ON state_transition_audits (delivery_id);
CREATE INDEX IF NOT EXISTS idx_tracking_breadcrumbs_delivery_id ON tracking_breadcrumbs (delivery_id);
CREATE INDEX IF NOT EXISTS idx_proof_of_delivery_delivery_id ON proof_of_delivery (delivery_id);
