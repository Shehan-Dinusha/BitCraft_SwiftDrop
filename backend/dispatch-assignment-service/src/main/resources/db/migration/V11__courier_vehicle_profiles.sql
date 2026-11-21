-- V11__courier_vehicle_profiles.sql
-- Couriers use their own vehicle. Instead of picking a vehicle type at every shift start, each courier
-- registers their vehicle once (after accepting their invite); their dispatcher can correct it. Shifts
-- copy the vehicle type and parcel capacity from this profile.
--
-- Bicycles are no longer a supported vehicle type: existing bicycle shifts become motorcycles.

UPDATE courier_shifts SET vehicle_type = 'MOTORCYCLE' WHERE vehicle_type = 'BICYCLE';

CREATE TABLE IF NOT EXISTS courier_profiles (
    id             BIGSERIAL PRIMARY KEY,
    -- IAM public id of the courier (no cross-service foreign key).
    courier_id     UUID         NOT NULL UNIQUE,
    -- Home district, copied from the courier's account; scopes which dispatcher manages the profile.
    district       VARCHAR(64)  NOT NULL,
    vehicle_type   VARCHAR(32)  NOT NULL,
    -- Normalised: upper case, single spaces, e.g. 'WP BAB-1234'.
    plate_number   VARCHAR(16)  NOT NULL,
    vehicle_model  VARCHAR(64)  NOT NULL,
    vehicle_colour VARCHAR(32)  NOT NULL,
    licence_number VARCHAR(16)  NOT NULL,
    max_capacity   INTEGER      NOT NULL,
    created_at     TIMESTAMPTZ  NOT NULL DEFAULT NOW(),
    updated_at     TIMESTAMPTZ  NOT NULL DEFAULT NOW(),
    version        BIGINT       NOT NULL DEFAULT 0,
    CONSTRAINT ck_courier_profiles_vehicle_type
        CHECK (vehicle_type IN ('MOTORCYCLE', 'THREE_WHEELER', 'VAN', 'LORRY')),
    CONSTRAINT ck_courier_profiles_capacity CHECK (max_capacity BETWEEN 1 AND 200)
);

CREATE UNIQUE INDEX IF NOT EXISTS ux_courier_profiles_plate_number ON courier_profiles (plate_number);
CREATE INDEX IF NOT EXISTS idx_courier_profiles_district ON courier_profiles (district);

COMMENT ON TABLE courier_profiles IS 'Each courier''s own registered vehicle and parcel capacity';
