-- V3: Make the default inter-district tariff and the volumetric divisor data-driven.
-- The 'ALL' / 'ALL' route is the default rule used when no route-specific rule exists.

ALTER TABLE tariff_rules
    ADD COLUMN IF NOT EXISTS volumetric_divisor NUMERIC(8, 2) NOT NULL DEFAULT 5000.00;

INSERT INTO tariff_rules (rule_version_id, from_district, to_district, base_price, weight_step_kg, price_per_step, volumetric_divisor, is_active)
VALUES ('PRICING-2026-V1', 'ALL', 'ALL', 550.00, 1.00, 120.00, 5000.00, true)
ON CONFLICT (rule_version_id, from_district, to_district) DO NOTHING;