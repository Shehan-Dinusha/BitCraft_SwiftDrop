# Analytics schema dictionary

Generated from the local analytics PostgreSQL catalog by `fixtures/export-analytics-schema.py`.
Schema metadata only: no application records, credentials or prompts are exported.

**Scope:** 10 application tables plus Flyway history, after V1–V5. No SQL foreign keys exist.
Dashed ERD relationships describe application-maintained identifier matches, not database constraints.

The exact constraints, defaults and indexes are also in [analytics-schema.json](analytics-schema.json).

## `ai_delivery_insights`

Persisted operational delay-risk predictions; separate from customer chat.

| Column | PostgreSQL type | Nullable | Key | Default |
|---|---|---|---|---|
| `id` | `bigint` | No | PK | `nextval('ai_delivery_insights_id_seq'::regclass)` |
| `insight_id` | `uuid` | No | UK | — |
| `tracking_number` | `character varying(64)` | Yes | — | — |
| `origin_district` | `character varying(64)` | No | — | — |
| `destination_district` | `character varying(64)` | No | — | — |
| `delivery_speed` | `character varying(64)` | No | — | — |
| `risk_level` | `character varying(32)` | No | — | — |
| `risk_score` | `numeric(5,2)` | No | — | — |
| `predicted_delay_minutes` | `integer` | No | — | `0` |
| `recommended_action` | `character varying(255)` | No | — | — |
| `reasoning` | `text` | No | — | — |
| `weather_or_traffic_factor` | `character varying(255)` | Yes | — | — |
| `created_at` | `timestamp with time zone` | No | — | `CURRENT_TIMESTAMP` |

Constraints:

- `ai_delivery_insights_insight_id_key`: `UNIQUE (insight_id)`
- `ai_delivery_insights_pkey`: `PRIMARY KEY (id)`

Indexes:

- `CREATE UNIQUE INDEX ai_delivery_insights_insight_id_key ON public.ai_delivery_insights USING btree (insight_id)`
- `CREATE UNIQUE INDEX ai_delivery_insights_pkey ON public.ai_delivery_insights USING btree (id)`
- `CREATE INDEX idx_ai_insights_risk ON public.ai_delivery_insights USING btree (risk_level)`
- `CREATE INDEX idx_ai_insights_tracking ON public.ai_delivery_insights USING btree (tracking_number)`

## `ai_tool_invocation_audit`

Metadata-only audits. Prompts and arguments remain null; output_result may contain a safe outcome code, never raw tool output.

| Column | PostgreSQL type | Nullable | Key | Default |
|---|---|---|---|---|
| `id` | `bigint` | No | PK | `nextval('ai_tool_invocation_audit_id_seq'::regclass)` |
| `audit_id` | `uuid` | No | UK | — |
| `tool_name` | `character varying(128)` | No | — | — |
| `caller_persona` | `character varying(64)` | No | — | — |
| `prompt_or_query` | `text` | Yes | — | — |
| `input_parameters` | `text` | Yes | — | — |
| `output_result` | `text` | Yes | — | — |
| `execution_time_ms` | `bigint` | No | — | `0` |
| `success` | `boolean` | No | — | `true` |
| `created_at` | `timestamp with time zone` | No | — | `CURRENT_TIMESTAMP` |
| `actor_user_id` | `uuid` | Yes | — | — |
| `correlation_id` | `character varying(100)` | Yes | — | — |
| `outcome` | `character varying(32)` | Yes | — | — |

Constraints:

- `ai_tool_invocation_audit_audit_id_key`: `UNIQUE (audit_id)`
- `ai_tool_invocation_audit_pkey`: `PRIMARY KEY (id)`

Indexes:

- `CREATE UNIQUE INDEX ai_tool_invocation_audit_audit_id_key ON public.ai_tool_invocation_audit USING btree (audit_id)`
- `CREATE UNIQUE INDEX ai_tool_invocation_audit_pkey ON public.ai_tool_invocation_audit USING btree (id)`
- `CREATE INDEX idx_ai_audit_actor_created ON public.ai_tool_invocation_audit USING btree (actor_user_id, created_at)`
- `CREATE INDEX idx_ai_audit_created ON public.ai_tool_invocation_audit USING btree (created_at)`
- `CREATE INDEX idx_ai_audit_tool ON public.ai_tool_invocation_audit USING btree (tool_name)`

## `analytics_couriers`

Known assigned couriers; not an online availability registry.

| Column | PostgreSQL type | Nullable | Key | Default |
|---|---|---|---|---|
| `courier_id` | `uuid` | No | PK | — |
| `courier_name` | `character varying(255)` | No | — | — |
| `last_assignment_at` | `timestamp with time zone` | No | — | — |

Constraints:

- `analytics_couriers_pkey`: `PRIMARY KEY (courier_id)`

Indexes:

- `CREATE UNIQUE INDEX analytics_couriers_pkey ON public.analytics_couriers USING btree (courier_id)`

## `analytics_order_couriers`

Distinct observed order/courier assignment pairs.

| Column | PostgreSQL type | Nullable | Key | Default |
|---|---|---|---|---|
| `order_id` | `uuid` | No | PK | — |
| `courier_id` | `uuid` | No | PK | — |

Constraints:

- `analytics_order_couriers_pkey`: `PRIMARY KEY (order_id, courier_id)`

Indexes:

- `CREATE UNIQUE INDEX analytics_order_couriers_pkey ON public.analytics_order_couriers USING btree (order_id, courier_id)`

## `analytics_order_projection`

Reduced per-order state, assignment, final outcome and conflict marker.

| Column | PostgreSQL type | Nullable | Key | Default |
|---|---|---|---|---|
| `order_id` | `uuid` | No | PK | — |
| `created_at` | `timestamp with time zone` | Yes | — | — |
| `pickup_district` | `character varying(64)` | Yes | — | — |
| `current_status` | `character varying(64)` | Yes | — | — |
| `status_at` | `timestamp with time zone` | Yes | — | — |
| `district` | `character varying(64)` | Yes | — | — |
| `courier_id` | `uuid` | Yes | — | — |
| `assigned_at` | `timestamp with time zone` | Yes | — | — |
| `outcome` | `character varying(64)` | Yes | — | — |
| `outcome_at` | `timestamp with time zone` | Yes | — | — |
| `outcome_district` | `character varying(64)` | Yes | — | — |
| `outcome_courier_id` | `uuid` | Yes | — | — |
| `conflict_reason` | `character varying(255)` | Yes | — | — |

Constraints:

- `analytics_order_projection_pkey`: `PRIMARY KEY (order_id)`

Indexes:

- `CREATE UNIQUE INDEX analytics_order_projection_pkey ON public.analytics_order_projection USING btree (order_id)`

## `courier_daily_stats`

Daily outcomes plus CURRENT active workload bucketed by assignment date.

| Column | PostgreSQL type | Nullable | Key | Default |
|---|---|---|---|---|
| `courier_id` | `uuid` | No | PK | — |
| `report_date` | `date` | No | PK | — |
| `delivered` | `bigint` | No | — | `0` |
| `failed` | `bigint` | No | — | `0` |
| `active` | `bigint` | No | — | `0` |

Constraints:

- `courier_daily_stats_active_check`: `CHECK ((active >= 0))`
- `courier_daily_stats_delivered_check`: `CHECK ((delivered >= 0))`
- `courier_daily_stats_failed_check`: `CHECK ((failed >= 0))`
- `courier_daily_stats_pkey`: `PRIMARY KEY (courier_id, report_date)`

Indexes:

- `CREATE UNIQUE INDEX courier_daily_stats_pkey ON public.courier_daily_stats USING btree (courier_id, report_date)`

## `courier_performance_metrics`

Legacy retained courier metrics; current reporting reads projections instead.

| Column | PostgreSQL type | Nullable | Key | Default |
|---|---|---|---|---|
| `id` | `bigint` | No | PK | `nextval('courier_performance_metrics_id_seq'::regclass)` |
| `courier_id` | `uuid` | No | UK | — |
| `courier_name` | `character varying(255)` | No | — | — |
| `current_district` | `character varying(64)` | No | — | — |
| `total_assigned` | `integer` | No | — | `0` |
| `delivered_count` | `integer` | No | — | `0` |
| `failed_count` | `integer` | No | — | `0` |
| `avg_transit_minutes` | `numeric(8,2)` | No | — | `0.0` |
| `on_time_percentage` | `numeric(5,2)` | No | — | `100.0` |
| `customer_rating` | `numeric(3,2)` | No | — | `5.0` |
| `status` | `character varying(64)` | No | — | `'AVAILABLE'::character varying` |
| `last_event_at` | `timestamp with time zone` | Yes | — | — |
| `created_at` | `timestamp with time zone` | No | — | `CURRENT_TIMESTAMP` |
| `updated_at` | `timestamp with time zone` | No | — | `CURRENT_TIMESTAMP` |

Constraints:

- `courier_performance_metrics_courier_id_key`: `UNIQUE (courier_id)`
- `courier_performance_metrics_pkey`: `PRIMARY KEY (id)`

Indexes:

- `CREATE UNIQUE INDEX courier_performance_metrics_courier_id_key ON public.courier_performance_metrics USING btree (courier_id)`
- `CREATE UNIQUE INDEX courier_performance_metrics_pkey ON public.courier_performance_metrics USING btree (id)`
- `CREATE INDEX idx_courier_perf_courier_id ON public.courier_performance_metrics USING btree (courier_id)`
- `CREATE INDEX idx_courier_perf_district ON public.courier_performance_metrics USING btree (current_district)`

## `daily_delivery_stats`

Daily creation and outcome counts in Asia/Colombo event time.

| Column | PostgreSQL type | Nullable | Key | Default |
|---|---|---|---|---|
| `report_date` | `date` | No | PK | — |
| `created` | `bigint` | No | — | `0` |
| `delivered` | `bigint` | No | — | `0` |
| `failed` | `bigint` | No | — | `0` |

Constraints:

- `daily_delivery_stats_created_check`: `CHECK ((created >= 0))`
- `daily_delivery_stats_delivered_check`: `CHECK ((delivered >= 0))`
- `daily_delivery_stats_failed_check`: `CHECK ((failed >= 0))`
- `daily_delivery_stats_pkey`: `PRIMARY KEY (report_date)`

Indexes:

- `CREATE UNIQUE INDEX daily_delivery_stats_pkey ON public.daily_delivery_stats USING btree (report_date)`

## `flyway_schema_history`

Flyway-managed migration metadata; not a business entity.

| Column | PostgreSQL type | Nullable | Key | Default |
|---|---|---|---|---|
| `installed_rank` | `integer` | No | PK | — |
| `version` | `character varying(50)` | Yes | — | — |
| `description` | `character varying(200)` | No | — | — |
| `type` | `character varying(20)` | No | — | — |
| `script` | `character varying(1000)` | No | — | — |
| `checksum` | `integer` | Yes | — | — |
| `installed_by` | `character varying(100)` | No | — | — |
| `installed_on` | `timestamp without time zone` | No | — | `now()` |
| `execution_time` | `integer` | No | — | — |
| `success` | `boolean` | No | — | — |

Constraints:

- `flyway_schema_history_pk`: `PRIMARY KEY (installed_rank)`

Indexes:

- `CREATE UNIQUE INDEX flyway_schema_history_pk ON public.flyway_schema_history USING btree (installed_rank)`
- `CREATE INDEX flyway_schema_history_s_idx ON public.flyway_schema_history USING btree (success)`

## `operational_sla_snapshots`

Legacy retained SLA snapshots; current reporting reads projections instead.

| Column | PostgreSQL type | Nullable | Key | Default |
|---|---|---|---|---|
| `id` | `bigint` | No | PK | `nextval('operational_sla_snapshots_id_seq'::regclass)` |
| `status` | `character varying(64)` | No | — | `'ACTIVE'::character varying` |
| `created_at` | `timestamp with time zone` | No | — | `CURRENT_TIMESTAMP` |
| `updated_at` | `timestamp with time zone` | No | — | `CURRENT_TIMESTAMP` |
| `district` | `character varying(64)` | Yes | — | — |
| `snapshot_date` | `date` | No | — | `CURRENT_DATE` |
| `total_parcels` | `integer` | No | — | `0` |
| `delivered_on_time` | `integer` | No | — | `0` |
| `breached_sla` | `integer` | No | — | `0` |
| `failed_deliveries` | `integer` | No | — | `0` |
| `avg_transit_time_minutes` | `numeric(8,2)` | No | — | `0.0` |
| `on_time_rate` | `numeric(5,2)` | No | — | `100.0` |

Constraints:

- `operational_sla_snapshots_pkey`: `PRIMARY KEY (id)`

Indexes:

- `CREATE INDEX idx_sla_snapshots_date ON public.operational_sla_snapshots USING btree (snapshot_date)`
- `CREATE INDEX idx_sla_snapshots_district ON public.operational_sla_snapshots USING btree (district)`
- `CREATE UNIQUE INDEX operational_sla_snapshots_pkey ON public.operational_sla_snapshots USING btree (id)`

## `processed_telemetry_events`

Idempotent event inbox and retained canonical payloads for projection replay.

| Column | PostgreSQL type | Nullable | Key | Default |
|---|---|---|---|---|
| `event_id` | `uuid` | No | PK | — |
| `event_type` | `character varying(64)` | No | — | — |
| `order_id` | `uuid` | No | — | — |
| `correlation_id` | `uuid` | No | — | — |
| `occurred_at` | `timestamp with time zone` | No | — | — |
| `payload` | `jsonb` | No | — | — |
| `processed_at` | `timestamp with time zone` | No | — | `CURRENT_TIMESTAMP` |
| `projection_applied` | `boolean` | No | — | `false` |

Constraints:

- `processed_telemetry_events_pkey`: `PRIMARY KEY (event_id)`

Indexes:

- `CREATE INDEX idx_processed_telemetry_order ON public.processed_telemetry_events USING btree (order_id)`
- `CREATE INDEX idx_telemetry_pending_projection ON public.processed_telemetry_events USING btree (order_id) WHERE (NOT projection_applied)`
- `CREATE UNIQUE INDEX processed_telemetry_events_pkey ON public.processed_telemetry_events USING btree (event_id)`
