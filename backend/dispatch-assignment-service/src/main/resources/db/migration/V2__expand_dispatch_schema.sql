-- V2__expand_dispatch_schema.sql
-- Expand dispatch_assignments and add courier_shifts table

ALTER TABLE dispatch_assignments
    ADD COLUMN IF NOT EXISTS assignment_id UUID NOT NULL DEFAULT gen_random_uuid(),
    ADD COLUMN IF NOT EXISTS order_id UUID,
    ADD COLUMN IF NOT EXISTS tracking_number VARCHAR(32),
    ADD COLUMN IF NOT EXISTS courier_id UUID,
    ADD COLUMN IF NOT EXISTS courier_name VARCHAR(128),
    ADD COLUMN IF NOT EXISTS pickup_district VARCHAR(64),
    ADD COLUMN IF NOT EXISTS delivery_district VARCHAR(64),
    ADD COLUMN IF NOT EXISTS order_speed VARCHAR(32) NOT NULL DEFAULT 'STANDARD',
    ADD COLUMN IF NOT EXISTS assigned_by VARCHAR(32) NOT NULL DEFAULT 'AUTO_ENGINE',
    ADD COLUMN IF NOT EXISTS assigned_at TIMESTAMP WITH TIME ZONE,
    ADD COLUMN IF NOT EXISTS estimated_pickup_minutes INT DEFAULT 30,
    ADD COLUMN IF NOT EXISTS failure_reason VARCHAR(512),
    ADD COLUMN IF NOT EXISTS retry_count INT NOT NULL DEFAULT 0;

ALTER TABLE dispatch_assignments ALTER COLUMN status SET DEFAULT 'UNASSIGNED';

CREATE TABLE IF NOT EXISTS courier_shifts (
    id BIGSERIAL PRIMARY KEY,
    courier_id UUID NOT NULL UNIQUE,
    courier_name VARCHAR(128) NOT NULL,
    phone VARCHAR(32),
    vehicle_type VARCHAR(32) DEFAULT 'MOTORCYCLE',
    current_district VARCHAR(64) NOT NULL,
    latitude NUMERIC(10, 7),
    longitude NUMERIC(10, 7),
    shift_status VARCHAR(32) NOT NULL DEFAULT 'OFFLINE',
    active_parcels_count INT NOT NULL DEFAULT 0,
    max_capacity INT NOT NULL DEFAULT 5,
    shift_started_at TIMESTAMP WITH TIME ZONE,
    shift_ended_at TIMESTAMP WITH TIME ZONE,
    last_heartbeat_at TIMESTAMP WITH TIME ZONE,
    created_at TIMESTAMP WITH TIME ZONE NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMP WITH TIME ZONE NOT NULL DEFAULT CURRENT_TIMESTAMP
);

CREATE UNIQUE INDEX IF NOT EXISTS uk_dispatch_assignments_assignment_id ON dispatch_assignments (assignment_id);
CREATE INDEX IF NOT EXISTS idx_dispatch_assignments_order_id ON dispatch_assignments (order_id);
CREATE INDEX IF NOT EXISTS idx_dispatch_assignments_courier_id ON dispatch_assignments (courier_id);
CREATE INDEX IF NOT EXISTS idx_dispatch_assignments_status ON dispatch_assignments (status);
CREATE INDEX IF NOT EXISTS idx_courier_shifts_status_district ON courier_shifts (shift_status, current_district);

-- Seed courier shifts for local testing
INSERT INTO courier_shifts (courier_id, courier_name, phone, vehicle_type, current_district, latitude, longitude, shift_status, active_parcels_count, max_capacity, shift_started_at, last_heartbeat_at)
VALUES
    ('11111111-1111-1111-1111-111111111111', 'Sunil Perera', '+94771234567', 'MOTORCYCLE', 'COLOMBO', 6.9271, 79.8612, 'AVAILABLE', 0, 5, CURRENT_TIMESTAMP, CURRENT_TIMESTAMP),
    ('22222222-2222-2222-2222-222222222222', 'Kamal Silva', '+94772345678', 'MOTORCYCLE', 'COLOMBO', 6.9319, 79.8478, 'AVAILABLE', 1, 5, CURRENT_TIMESTAMP, CURRENT_TIMESTAMP),
    ('33333333-3333-3333-3333-333333333333', 'Nimal Fernando', '+94773456789', 'VAN', 'GAMPAHA', 7.0840, 79.9939, 'AVAILABLE', 0, 8, CURRENT_TIMESTAMP, CURRENT_TIMESTAMP),
    ('44444444-4444-4444-4444-444444444444', 'Roshan Bandara', '+94774567890', 'MOTORCYCLE', 'KANDY', 7.2906, 80.6337, 'AVAILABLE', 0, 5, CURRENT_TIMESTAMP, CURRENT_TIMESTAMP)
ON CONFLICT (courier_id) DO NOTHING;
