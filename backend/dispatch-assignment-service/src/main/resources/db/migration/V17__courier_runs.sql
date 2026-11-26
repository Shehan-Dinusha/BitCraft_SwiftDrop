-- V17__courier_runs.sql
-- A courier's day starts at the hub: they load the parcels waiting there for them, then leave on their run.
-- Until they leave they can be given hub parcels; once on the road only pickups (until 15:00).

ALTER TABLE courier_shifts ADD COLUMN IF NOT EXISTS run_started_at TIMESTAMPTZ;

COMMENT ON COLUMN courier_shifts.run_started_at IS 'When the courier left the hub on their run; null while at the hub';
