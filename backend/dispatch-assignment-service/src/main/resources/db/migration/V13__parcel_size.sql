-- Parcel size and weight, so a parcel is only assigned to a courier whose vehicle can carry it. Nullable:
-- orders booked before this have no recorded size and keep the parcel-count rule only.
ALTER TABLE dispatch_assignments
    ADD COLUMN IF NOT EXISTS length_cm NUMERIC(8, 2),
    ADD COLUMN IF NOT EXISTS width_cm NUMERIC(8, 2),
    ADD COLUMN IF NOT EXISTS height_cm NUMERIC(8, 2),
    ADD COLUMN IF NOT EXISTS weight_kg NUMERIC(8, 2),
    -- length x width x height, stored so a courier's load is a plain sum.
    ADD COLUMN IF NOT EXISTS volume_litres NUMERIC(12, 2);
