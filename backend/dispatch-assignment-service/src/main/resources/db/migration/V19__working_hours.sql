-- V19__working_hours.sql
-- The courier working day becomes editable by a SYSTEM_ADMIN (one row, id 1). The defaults are the agreed
-- plan: Monday to Saturday, 8:00 to 17:00 Sri Lanka time, check-in from 7:30 (late after 9:00), one lunch
-- break of 45 minutes started between 12:00 and 14:30, pickups until 15:00 and Same day bookings until 11:00.

CREATE TABLE IF NOT EXISTS working_hours (
    id              SMALLINT     PRIMARY KEY CHECK (id = 1),
    working_days    VARCHAR(80)  NOT NULL,
    opens_at        TIME         NOT NULL,
    closes_at       TIME         NOT NULL,
    shift_from      TIME         NOT NULL,
    late_after      TIME         NOT NULL,
    break_from      TIME         NOT NULL,
    break_until     TIME         NOT NULL,
    break_minutes   INTEGER      NOT NULL,
    pickup_cutoff   TIME         NOT NULL,
    same_day_cutoff TIME         NOT NULL,
    updated_at      TIMESTAMPTZ  NOT NULL DEFAULT NOW(),
    updated_by      UUID
);

COMMENT ON TABLE working_hours IS 'Courier working hours set by a SYSTEM_ADMIN; exactly one row';
COMMENT ON COLUMN working_hours.working_days IS 'Comma-separated weekdays couriers work, e.g. MONDAY,TUESDAY';
COMMENT ON COLUMN working_hours.same_day_cutoff IS 'Same day parcels are booked before this; read by the order service';

INSERT INTO working_hours (id, working_days, opens_at, closes_at, shift_from, late_after, break_from, break_until,
                           break_minutes, pickup_cutoff, same_day_cutoff)
VALUES (1, 'MONDAY,TUESDAY,WEDNESDAY,THURSDAY,FRIDAY,SATURDAY', '08:00', '17:00', '07:30', '09:00', '12:00',
        '14:30', 45, '15:00', '11:00')
ON CONFLICT (id) DO NOTHING;
