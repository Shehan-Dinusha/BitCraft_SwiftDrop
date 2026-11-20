-- SwiftDrop does not use device location. Remove the GPS breadcrumb trail and the coordinates
-- that were stored on delivery sessions and state-transition audits.
DROP TABLE IF EXISTS tracking_breadcrumbs;

ALTER TABLE delivery_sessions
    DROP COLUMN IF EXISTS current_latitude,
    DROP COLUMN IF EXISTS current_longitude;

ALTER TABLE state_transition_audits
    DROP COLUMN IF EXISTS latitude,
    DROP COLUMN IF EXISTS longitude;
