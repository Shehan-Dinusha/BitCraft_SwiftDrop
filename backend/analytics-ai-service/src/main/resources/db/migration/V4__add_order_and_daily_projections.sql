ALTER TABLE processed_telemetry_events ADD COLUMN projection_applied BOOLEAN NOT NULL DEFAULT FALSE;
CREATE INDEX idx_telemetry_pending_projection ON processed_telemetry_events(order_id) WHERE NOT projection_applied;

CREATE TABLE analytics_order_projection (
    order_id UUID PRIMARY KEY,
    created_at TIMESTAMPTZ,
    pickup_district VARCHAR(64),
    current_status VARCHAR(64),
    status_at TIMESTAMPTZ,
    district VARCHAR(64),
    courier_id UUID,
    assigned_at TIMESTAMPTZ,
    outcome VARCHAR(64),
    outcome_at TIMESTAMPTZ,
    outcome_district VARCHAR(64),
    outcome_courier_id UUID,
    conflict_reason VARCHAR(255)
);

CREATE TABLE daily_delivery_stats (
    report_date DATE PRIMARY KEY,
    created BIGINT NOT NULL DEFAULT 0 CHECK (created >= 0),
    delivered BIGINT NOT NULL DEFAULT 0 CHECK (delivered >= 0),
    failed BIGINT NOT NULL DEFAULT 0 CHECK (failed >= 0)
);

CREATE TABLE analytics_couriers (
    courier_id UUID PRIMARY KEY,
    courier_name VARCHAR(255) NOT NULL,
    last_assignment_at TIMESTAMPTZ NOT NULL
);

-- Internal analytics relationships only; these are not cross-service foreign keys.
CREATE TABLE analytics_order_couriers (
    order_id UUID NOT NULL,
    courier_id UUID NOT NULL,
    PRIMARY KEY (order_id, courier_id)
);

CREATE TABLE courier_daily_stats (
    courier_id UUID NOT NULL,
    report_date DATE NOT NULL,
    delivered BIGINT NOT NULL DEFAULT 0 CHECK (delivered >= 0),
    failed BIGINT NOT NULL DEFAULT 0 CHECK (failed >= 0),
    active BIGINT NOT NULL DEFAULT 0 CHECK (active >= 0),
    PRIMARY KEY (courier_id, report_date)
);
-- active is CURRENT workload bucketed by the effective assignment date, not a
-- historical end-of-day snapshot. Sum all dates to obtain current workload.
-- Legacy SLA/courier tables are preserved for inspection but no longer feed reads.
