-- V2__expand_analytics_schema.sql
-- Expands analytics schema for Operational SLAs, Courier Performance, AI Insights, and Tool Invocations

ALTER TABLE operational_sla_snapshots ADD COLUMN IF NOT EXISTS district VARCHAR(64);
ALTER TABLE operational_sla_snapshots ADD COLUMN IF NOT EXISTS snapshot_date DATE NOT NULL DEFAULT CURRENT_DATE;
ALTER TABLE operational_sla_snapshots ADD COLUMN IF NOT EXISTS total_parcels INT NOT NULL DEFAULT 0;
ALTER TABLE operational_sla_snapshots ADD COLUMN IF NOT EXISTS delivered_on_time INT NOT NULL DEFAULT 0;
ALTER TABLE operational_sla_snapshots ADD COLUMN IF NOT EXISTS breached_sla INT NOT NULL DEFAULT 0;
ALTER TABLE operational_sla_snapshots ADD COLUMN IF NOT EXISTS failed_deliveries INT NOT NULL DEFAULT 0;
ALTER TABLE operational_sla_snapshots ADD COLUMN IF NOT EXISTS avg_transit_time_minutes NUMERIC(8,2) NOT NULL DEFAULT 0.0;
ALTER TABLE operational_sla_snapshots ADD COLUMN IF NOT EXISTS on_time_rate NUMERIC(5,2) NOT NULL DEFAULT 100.0;

CREATE INDEX IF NOT EXISTS idx_sla_snapshots_district ON operational_sla_snapshots(district);
CREATE INDEX IF NOT EXISTS idx_sla_snapshots_date ON operational_sla_snapshots(snapshot_date);

CREATE TABLE IF NOT EXISTS courier_performance_metrics (
    id BIGSERIAL PRIMARY KEY,
    courier_id UUID NOT NULL UNIQUE,
    courier_name VARCHAR(255) NOT NULL,
    current_district VARCHAR(64) NOT NULL,
    total_assigned INT NOT NULL DEFAULT 0,
    delivered_count INT NOT NULL DEFAULT 0,
    failed_count INT NOT NULL DEFAULT 0,
    avg_transit_minutes NUMERIC(8,2) NOT NULL DEFAULT 0.0,
    on_time_percentage NUMERIC(5,2) NOT NULL DEFAULT 100.0,
    customer_rating NUMERIC(3,2) NOT NULL DEFAULT 5.0,
    status VARCHAR(64) NOT NULL DEFAULT 'AVAILABLE',
    last_event_at TIMESTAMP WITH TIME ZONE,
    created_at TIMESTAMP WITH TIME ZONE NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMP WITH TIME ZONE NOT NULL DEFAULT CURRENT_TIMESTAMP
);

CREATE INDEX IF NOT EXISTS idx_courier_perf_district ON courier_performance_metrics(current_district);
CREATE INDEX IF NOT EXISTS idx_courier_perf_courier_id ON courier_performance_metrics(courier_id);

CREATE TABLE IF NOT EXISTS ai_delivery_insights (
    id BIGSERIAL PRIMARY KEY,
    insight_id UUID NOT NULL UNIQUE,
    tracking_number VARCHAR(64),
    origin_district VARCHAR(64) NOT NULL,
    destination_district VARCHAR(64) NOT NULL,
    delivery_speed VARCHAR(64) NOT NULL,
    risk_level VARCHAR(32) NOT NULL,
    risk_score NUMERIC(5,2) NOT NULL,
    predicted_delay_minutes INT NOT NULL DEFAULT 0,
    recommended_action VARCHAR(255) NOT NULL,
    reasoning TEXT NOT NULL,
    weather_or_traffic_factor VARCHAR(255),
    created_at TIMESTAMP WITH TIME ZONE NOT NULL DEFAULT CURRENT_TIMESTAMP
);

CREATE INDEX IF NOT EXISTS idx_ai_insights_tracking ON ai_delivery_insights(tracking_number);
CREATE INDEX IF NOT EXISTS idx_ai_insights_risk ON ai_delivery_insights(risk_level);

CREATE TABLE IF NOT EXISTS ai_tool_invocation_audit (
    id BIGSERIAL PRIMARY KEY,
    audit_id UUID NOT NULL UNIQUE,
    tool_name VARCHAR(128) NOT NULL,
    caller_persona VARCHAR(64) NOT NULL,
    prompt_or_query TEXT NOT NULL,
    input_parameters TEXT,
    output_result TEXT,
    execution_time_ms BIGINT NOT NULL DEFAULT 0,
    success BOOLEAN NOT NULL DEFAULT true,
    created_at TIMESTAMP WITH TIME ZONE NOT NULL DEFAULT CURRENT_TIMESTAMP
);

CREATE INDEX IF NOT EXISTS idx_ai_audit_tool ON ai_tool_invocation_audit(tool_name);
CREATE INDEX IF NOT EXISTS idx_ai_audit_created ON ai_tool_invocation_audit(created_at);
