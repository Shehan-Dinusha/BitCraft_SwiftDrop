-- SwiftDrop does not use device location. Couriers are matched by district, so the GPS
-- coordinates stored on courier shifts are dropped.
ALTER TABLE courier_shifts
    DROP COLUMN IF EXISTS latitude,
    DROP COLUMN IF EXISTS longitude;
