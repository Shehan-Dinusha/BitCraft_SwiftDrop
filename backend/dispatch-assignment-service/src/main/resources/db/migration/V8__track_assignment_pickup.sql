-- V8__track_assignment_pickup.sql
-- Set when delivery-execution-service reports that the parcel has passed the
-- pickup boundary (PICKED_UP, IN_TRANSIT or OUT_FOR_DELIVERY). Normal
-- reassignment is rejected once it is set.
--
-- Nullable with no backfill: dispatch cannot read the delivery database, so
-- existing in-flight assignments stay NULL until their next post-pickup event.
ALTER TABLE dispatch_assignments
    ADD COLUMN IF NOT EXISTS picked_up_at TIMESTAMP WITH TIME ZONE;
