-- District reports. A courier's district is the one that assigned their latest order.
ALTER TABLE analytics_couriers ADD COLUMN district VARCHAR(64);

CREATE INDEX idx_projection_pickup_created ON analytics_order_projection (pickup_district, created_at);
CREATE INDEX idx_projection_outcome ON analytics_order_projection (outcome_district, outcome_at);

-- Rebuild every order from its stored events when the service starts: an outcome now belongs to the
-- district that was handling the parcel (the drop-off one once its hub accepted it), and a courier who
-- handed a parcel in at a hub no longer counts as carrying it. Only derived rows change.
UPDATE processed_telemetry_events SET projection_applied = FALSE;
