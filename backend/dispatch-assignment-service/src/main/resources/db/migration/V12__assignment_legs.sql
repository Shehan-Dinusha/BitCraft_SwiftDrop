-- Two-leg delivery between districts. An order picked up in one district and delivered in another is
-- carried by a courier of the pickup district to its hub (FIRST_MILE), sent on to the drop-off district's
-- hub, and taken to the recipient by a courier of that district (LAST_MILE). The pickup district's
-- dispatcher manages the first leg, the drop-off district's dispatcher the last.
ALTER TABLE dispatch_assignments
    ADD COLUMN IF NOT EXISTS leg VARCHAR(16) NOT NULL DEFAULT 'FIRST_MILE',
    -- Where the parcel is between the legs: AT_HUB, OUTBOUND or INCOMING (InternalQueueStatus).
    ADD COLUMN IF NOT EXISTS hub_stage VARCHAR(32),
    -- The courier who carried the first leg, kept after the parcel was handed in at the hub.
    ADD COLUMN IF NOT EXISTS first_mile_courier_id UUID;

CREATE INDEX IF NOT EXISTS idx_dispatch_assignments_hub_stage ON dispatch_assignments (hub_stage)
    WHERE hub_stage IS NOT NULL;
