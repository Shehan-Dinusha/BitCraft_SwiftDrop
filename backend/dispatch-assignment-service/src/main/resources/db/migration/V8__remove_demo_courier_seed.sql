-- V8__remove_demo_courier_seed.sql
-- V2 seeded four demo couriers for local testing. They have no user accounts
-- in auth-iam-service, so they are not real couriers and must not be offered to
-- dispatchers or receive assignments.
--
-- Safety rules:
--   * Only these four exact courier IDs are targeted.
--   * A demo courier is removed only when no dispatch assignment references it.
--     Referenced rows are kept untouched (no cascade, no assignment changes) and
--     reported with a NOTICE for manual review.
--   * Re-running is harmless: rows that are already gone are simply not matched.

DELETE FROM courier_shifts cs
WHERE cs.courier_id IN (
        '11111111-1111-1111-1111-111111111111',
        '22222222-2222-2222-2222-222222222222',
        '33333333-3333-3333-3333-333333333333',
        '44444444-4444-4444-4444-444444444444'
    )
  AND NOT EXISTS (
        SELECT 1
        FROM dispatch_assignments da
        WHERE da.courier_id = cs.courier_id
    );

DO $$
DECLARE
    kept_count INTEGER;
BEGIN
    SELECT count(*) INTO kept_count
    FROM courier_shifts
    WHERE courier_id IN (
        '11111111-1111-1111-1111-111111111111',
        '22222222-2222-2222-2222-222222222222',
        '33333333-3333-3333-3333-333333333333',
        '44444444-4444-4444-4444-444444444444'
    );

    IF kept_count > 0 THEN
        RAISE NOTICE 'V8 kept % demo courier record(s) because dispatch assignments still reference them; review them manually',
            kept_count;
    END IF;
END $$;
