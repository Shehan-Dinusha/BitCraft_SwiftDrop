-- V18__back_at_hub.sql
-- A courier comes back to the hub during or at the end of the day: their run ends, they hand in what they carry
-- and can load again. The time they last came back shows what to hand in.

ALTER TABLE courier_shifts ADD COLUMN IF NOT EXISTS last_run_ended_at TIMESTAMPTZ;

COMMENT ON COLUMN courier_shifts.last_run_ended_at IS 'When the courier last came back to the hub from a run this shift';
