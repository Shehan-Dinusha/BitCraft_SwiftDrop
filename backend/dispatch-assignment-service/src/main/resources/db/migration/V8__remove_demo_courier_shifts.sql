-- V2 inserted four demo courier shifts for early local testing (ids 1111…, 2222…, 3333…, 4444…).
-- They have no user account, so nobody can sign in as them, yet they were AVAILABLE: the
-- auto-engine assigned real orders to them and those parcels could never be delivered.
-- Real couriers create their shift when they start one, so the demo rows are removed.
-- Assignments that already point at them (if any) are left as they are; a dispatcher can
-- reassign them (ManualOverrideService tolerates a previous courier that no longer exists).
DELETE FROM courier_shifts
WHERE courier_id IN (
    '11111111-1111-1111-1111-111111111111',
    '22222222-2222-2222-2222-222222222222',
    '33333333-3333-3333-3333-333333333333',
    '44444444-4444-4444-4444-444444444444'
);
