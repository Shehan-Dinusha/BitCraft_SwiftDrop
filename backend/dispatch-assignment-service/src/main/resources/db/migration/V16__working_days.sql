-- V16__working_days.sql
-- Couriers work Monday to Saturday, 8:00 to 17:00 Sri Lanka time. Sundays are always closed; other closed
-- days (Poya days, public holidays) are listed here by a SYSTEM_ADMIN.
--
-- Each shift has one lunch break, and a shift still open at night is closed automatically and flagged.

CREATE TABLE IF NOT EXISTS closed_days (
    day        DATE         PRIMARY KEY,
    name       VARCHAR(64)  NOT NULL,
    created_at TIMESTAMPTZ  NOT NULL DEFAULT NOW()
);

COMMENT ON TABLE closed_days IS 'Holidays on which nobody works; Sundays are closed without being listed';

ALTER TABLE courier_shifts ADD COLUMN IF NOT EXISTS break_started_at TIMESTAMPTZ;
ALTER TABLE courier_shifts ADD COLUMN IF NOT EXISTS break_ended_at TIMESTAMPTZ;
ALTER TABLE courier_shifts ADD COLUMN IF NOT EXISTS closed_automatically BOOLEAN NOT NULL DEFAULT FALSE;

COMMENT ON COLUMN courier_shifts.break_started_at IS 'The lunch break of the current shift; one per shift';
COMMENT ON COLUMN courier_shifts.closed_automatically IS 'The courier left the shift open and it was closed at night';
