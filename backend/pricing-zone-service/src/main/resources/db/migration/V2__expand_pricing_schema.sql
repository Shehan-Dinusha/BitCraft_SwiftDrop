-- V2: Expand pricing schema for geofence zones, district mappings, and tariff rules
-- Aligns with V1 table pricing_zones

ALTER TABLE pricing_zones
    ADD COLUMN IF NOT EXISTS zone_code VARCHAR(32),
    ADD COLUMN IF NOT EXISTS zone_name VARCHAR(128),
    ADD COLUMN IF NOT EXISTS tier VARCHAR(32) NOT NULL DEFAULT 'URBAN',
    ADD COLUMN IF NOT EXISTS base_fee NUMERIC(10, 2) NOT NULL DEFAULT 350.00,
    ADD COLUMN IF NOT EXISTS per_kg_fee NUMERIC(10, 2) NOT NULL DEFAULT 80.00,
    ADD COLUMN IF NOT EXISTS express_multiplier NUMERIC(4, 2) NOT NULL DEFAULT 1.50,
    ADD COLUMN IF NOT EXISTS same_day_multiplier NUMERIC(4, 2) NOT NULL DEFAULT 2.00;

CREATE UNIQUE INDEX IF NOT EXISTS uk_pricing_zones_zone_code ON pricing_zones(zone_code);

CREATE TABLE IF NOT EXISTS district_zone_mappings (
    district VARCHAR(64) PRIMARY KEY,
    zone_code VARCHAR(32) NOT NULL,
    created_at TIMESTAMP WITH TIME ZONE NOT NULL DEFAULT CURRENT_TIMESTAMP
);

CREATE TABLE IF NOT EXISTS tariff_rules (
    id BIGSERIAL PRIMARY KEY,
    rule_version_id VARCHAR(64) NOT NULL,
    from_district VARCHAR(64) NOT NULL,
    to_district VARCHAR(64) NOT NULL,
    base_price NUMERIC(10, 2) NOT NULL,
    weight_step_kg NUMERIC(6, 2) NOT NULL DEFAULT 1.00,
    price_per_step NUMERIC(10, 2) NOT NULL DEFAULT 100.00,
    is_active BOOLEAN NOT NULL DEFAULT true,
    created_at TIMESTAMP WITH TIME ZONE NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMP WITH TIME ZONE NOT NULL DEFAULT CURRENT_TIMESTAMP,
    CONSTRAINT uk_tariff_route UNIQUE (rule_version_id, from_district, to_district)
);

-- Seed default pricing zones
INSERT INTO pricing_zones (zone_code, zone_name, tier, base_fee, per_kg_fee, express_multiplier, same_day_multiplier, status)
VALUES
    ('ZONE_WESTERN', 'Western Province Metro', 'METRO', 350.00, 80.00, 1.50, 2.00, 'ACTIVE'),
    ('ZONE_CENTRAL', 'Central Province Hills', 'URBAN', 450.00, 100.00, 1.50, 2.00, 'ACTIVE'),
    ('ZONE_SOUTHERN', 'Southern Coastal', 'URBAN', 420.00, 90.00, 1.50, 2.00, 'ACTIVE'),
    ('ZONE_NORTHERN', 'Northern Province', 'REGIONAL', 550.00, 120.00, 1.50, 2.00, 'ACTIVE'),
    ('ZONE_EASTERN', 'Eastern Province', 'REGIONAL', 520.00, 110.00, 1.50, 2.00, 'ACTIVE'),
    ('ZONE_NORTH_WESTERN', 'North Western Province', 'URBAN', 420.00, 90.00, 1.50, 2.00, 'ACTIVE'),
    ('ZONE_NORTH_CENTRAL', 'North Central Province', 'REGIONAL', 500.00, 110.00, 1.50, 2.00, 'ACTIVE'),
    ('ZONE_UVA', 'Uva Province', 'REMOTE', 550.00, 120.00, 1.50, 2.00, 'ACTIVE'),
    ('ZONE_SABARAGAMUWA', 'Sabaragamuwa Province', 'URBAN', 440.00, 95.00, 1.50, 2.00, 'ACTIVE')
ON CONFLICT (zone_code) DO NOTHING;

-- Seed district mappings for all 25 Sri Lankan districts
INSERT INTO district_zone_mappings (district, zone_code) VALUES
    ('COLOMBO', 'ZONE_WESTERN'),
    ('GAMPAHA', 'ZONE_WESTERN'),
    ('KALUTARA', 'ZONE_WESTERN'),
    ('KANDY', 'ZONE_CENTRAL'),
    ('MATALE', 'ZONE_CENTRAL'),
    ('NUWARA_ELIYA', 'ZONE_CENTRAL'),
    ('GALLE', 'ZONE_SOUTHERN'),
    ('MATARA', 'ZONE_SOUTHERN'),
    ('HAMBANTOTA', 'ZONE_SOUTHERN'),
    ('JAFFNA', 'ZONE_NORTHERN'),
    ('KILINOCHCHI', 'ZONE_NORTHERN'),
    ('MANNAR', 'ZONE_NORTHERN'),
    ('VAVUNIYA', 'ZONE_NORTHERN'),
    ('MULLAITIVU', 'ZONE_NORTHERN'),
    ('BATTICALOA', 'ZONE_EASTERN'),
    ('AMPARA', 'ZONE_EASTERN'),
    ('TRINCOMALEE', 'ZONE_EASTERN'),
    ('KURUNEGALA', 'ZONE_NORTH_WESTERN'),
    ('PUTTALAM', 'ZONE_NORTH_WESTERN'),
    ('ANURADHAPURA', 'ZONE_NORTH_CENTRAL'),
    ('POLONNARUWA', 'ZONE_NORTH_CENTRAL'),
    ('BADULLA', 'ZONE_UVA'),
    ('MONARAGALA', 'ZONE_UVA'),
    ('RATNAPURA', 'ZONE_SABARAGAMUWA'),
    ('KEGALLE', 'ZONE_SABARAGAMUWA')
ON CONFLICT (district) DO NOTHING;

CREATE TABLE IF NOT EXISTS surge_multiplier (
    id BIGSERIAL PRIMARY KEY,
    zone_code VARCHAR(32),
    multiplier NUMERIC(4, 2) NOT NULL DEFAULT 1.0,
    created_at TIMESTAMP WITH TIME ZONE NOT NULL DEFAULT CURRENT_TIMESTAMP
);

