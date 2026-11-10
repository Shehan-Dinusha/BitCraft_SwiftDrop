# Member 10 baseline

## Revision and scope

- Inspected on 2026-11-08, reporting/local timezone `Asia/Colombo`.
- Starting commit: `531f125dd485bda777d88a54f66e960839218c81`.
- Working branch: `Buddhika/feat/baseline-contracts`; the user's existing branch was retained.
- Part 1 captures evidence and defines contracts. Parts 2-9 implement the fixes and features.
- Toolchain observed: Temurin Java 25.0.5, Maven 3.9.11, Node 22.15.1, npm 11.10.1, Docker 29.8.1, Compose v5.5.1. Spring Boot 3.5.16 and PostgreSQL 16.15 were observed at runtime.

## Ownership and data flow

```text
M3 order creation -------------> order.created -----------+ (binding still needed)
M5 dispatch assignment --------> dispatch.courier.assigned +--> RabbitMQ --> M10 consumer
M6 delivery transitions -------> parcel.status.changed ---+                   |
                                                                              v
                                                                  M10 PostgreSQL projections
                                                                              |
Browser --> M1 gateway --> M10 reports/assistant --> gateway --> M1 tracking / M4 pricing
```

M10 owns analytics persistence and assistant orchestration. M1 owns public tracking and the gateway; M4 owns pricing; M6 owns delivery state transitions. Analytics must not query another service's database. Frontend calls use the gateway, not port 8089. Direct-port calls below are diagnostic baseline checks.

## Source inventory

Paths below are relative to the repository root.

| Area | Source inspected | Finding |
|---|---|---|
| HTTP reports | `backend/analytics-ai-service/src/main/java/com/swiftdrop/analytics/controller/OperationalKpiController.java` | KPI, courier, and aggregation handlers; no report role checks |
| Aggregation | Same module, `service/SlaMetricsAggregationService.java` | Read-time seeding, receipt-date aggregation, counters, fixed fallback values |
| Consumer | Same module, `messaging/consumer/TelemetryAggregationConsumer.java` | Status/assignment objects or map heuristics; no persisted event deduplication |
| Broker declarations | Same module, `config/RabbitMQConfig.java`; `infra/local/rabbitmq/definitions.json` | Same queue names and routing patterns, incompatible queue arguments |
| AI entry point | Same module, `controller/AiDeliveryAssistantController.java`, `service/AiDeliveryAssistantService.java` | Existing prediction/reroute/query/audit endpoints |
| Agent/tools | Same module, `ai/DeliveryAssistantAgent.java`, `ai/tools/OrderLookupTool.java` | Keyword intent detection and fabricated order snapshots |
| Storage | Same module, `domain/entity/`, `domain/repository/`, `src/main/resources/db/migration/` | Four application tables; no per-order projection or processed-event table |
| Shared contracts | `backend/common-contracts/src/main/java/com/swiftdrop/contracts/` | Event records, status enums, envelopes, and verified header constants |
| Pricing | `backend/pricing-zone-service/src/main/java/com/swiftdrop/pricing/controller/PricingQuoteController.java`, `dto/EstimateRequest.java`, `dto/EstimateResponse.java` | District-based estimate contract |
| Tracking | `backend/api-gateway/src/main/java/com/swiftdrop/gateway/trackinghub/controller/PublicTrackingController.java`, `dto/PublicTrackingViewDto.java` | Sanitized DTO returned directly, with order-service cache-miss fallback |
| Edge identity | Gateway `config/GatewayRoutesConfig.java`, `filter/JwtAuthenticationGlobalFilter.java` | Existing analytics aliases routed; verified identity headers injected after JWT validation |
| Frontend | `frontend/src/services/api/analyticsAiApi.ts`, `src/types/generated/analytics-ai-service.ts`, `src/hooks/useAiAssistant.ts`, admin analytics page, dock/slot, design tokens | Existing API adapters; placeholder page; slot returns null; hook only opens/closes |
| Documentation | `docs/api-specs/analytics-ai-service.yaml`, pricing spec, `docs/member-guides/member-10.md`, shared ownership guide, local development guide | Published baseline versus future requirements |

## Repeatable local startup

Use PowerShell at the repository root. Ensure Docker Desktop is running. Do not replace an existing `.env`; Compose reads it, while Maven processes need their own environment values. Defaults below match the current local analytics YAML and Compose defaults; custom installations must set matching values in their terminal.

```powershell
java -version
mvn -version
node --version
npm.cmd --version
docker compose version

docker compose config --quiet
docker compose up -d rabbitmq analytics-postgres
docker compose ps rabbitmq analytics-postgres

mvn -f backend/pom.xml -pl common-contracts -am -DskipTests install
```

In a service terminal:

```powershell
mvn -f backend/analytics-ai-service/pom.xml spring-boot:run
```

Run the application POM directly after installing common contracts. Applying `spring-boot:run` to the parent with `-am` can attempt to run the parent/library as an application.

Current local defaults: application `8089`, database `localhost:5440/swiftdrop_analytics`, PostgreSQL user `swiftdrop`, local development password `changeme_pg`; RabbitMQ `localhost:5672`, local user `swiftdrop`, local development password `changeme_rabbit`.

If overridden credentials are used, set `DB_URL`, `DB_USERNAME`, `DB_PASSWORD`, `RABBITMQ_HOST`, `RABBITMQ_PORT`, `RABBITMQ_USERNAME`, and `RABBITMQ_PASSWORD` as appropriate before launching Maven. The old guide's credential mismatch is already fixed in current analytics local YAML; no credential code change is needed in Part 1.

In a separate gateway terminal:

```powershell
mvn -f backend/api-gateway/pom.xml spring-boot:run
```

The gateway's full health also depends on Redis. For a fully provisioned local gateway, start `docker compose up -d redis` first. During this baseline Redis was not started: gateway health returned 503, but the analytics documentation route returned 200 and the unauthenticated KPI route returned 401. No authenticated end-to-end role claim is made.

In a frontend terminal, if needed:

```powershell
Push-Location frontend
# Run npm.cmd ci first only if dependencies are not already installed.
npm.cmd run dev
Pop-Location
```

Vite normally serves `http://localhost:5173`; the shared client uses `/api` and the dev proxy routes to gateway `8080`.

## Existing HTTP behavior

All analytics successes use `ApiResponse<T>`. `ApiResponse.success(String)` sets the message and omits null `data`; health is not a string inside `data`. Unknown courier returns an empty 404 body. Existing unexpected runtime exceptions are broadly translated to 400 by `GlobalExceptionHandler`, exposing internal error text.

| Method and path | Source behavior | Observed live in Part 1 |
|---|---|---|
| `GET :8089/actuator/health` | Infrastructure health | 200, `{"status":"UP"}` despite listener declaration errors |
| `GET /api/v1/analytics/health` | Controller readiness message | Direct 200, message `OperationalKpiController is ready` |
| `GET /api/v1/analytics/kpis` | Today's snapshots; attempts to seed if empty | Direct 400: PostgreSQL rejects INSERT in read-only transaction |
| `GET /api/v1/analytics/couriers?district=...` | All couriers or district filter | Direct unfiltered 200 with `data: []` |
| `GET /api/v1/analytics/couriers/{courierId}` | Optional courier lookup | Direct unknown UUID 404 with empty body |
| `POST /api/v1/analytics/snapshots/aggregate` | Calls the same KPI read method | Source inspected; not invoked |
| `GET /api/v1/analytics/ai/health` | AI controller readiness message | Direct 200 |
| `POST /api/v1/analytics/ai/predict-delay` | Heuristic prediction; persists insight | Source inspected; not invoked |
| `POST /api/v1/analytics/ai/reroute-suggestions` | Heuristic reroute calculation | Source inspected; not invoked |
| `POST /api/v1/analytics/ai/agent-query` | Keyword agent; persists raw query/context/answer | Source inspected; not invoked |
| `GET /api/v1/analytics/ai/audits` | Recent audit records | Source inspected; not invoked |
| `GET :8080/api/v1/analytics/kpis` without token | Gateway JWT check | 401 |
| `GET :8080/api/v1/analytics/v3/api-docs` | Rewrite to analytics `/v3/api-docs` | 200, OpenAPI 3.1.0 |

Operational aliases exist under `/api/v1/operational-kpi`; assistant aliases exist under `/api/v1/ai-delivery-assistant`. The live document contains 20 paths including aliases; the static analytics spec lists the 10 canonical paths. Treat alias/schema naming differences separately from a payload-breaking change.

The new daily/success/workload reports and `/api/v1/ai/chat` do not exist yet. The gateway does not route `/api/v1/ai/**` yet.

Cross-service contract drift: pricing Java implements `/api/v1/pricing/estimate`, but the checked-in `docs/api-specs/pricing-zone-service.yaml` currently lists health, quote, and zones only. M4 must publish the estimate operation/schema before generated types can represent that interface reliably.

Captured responses and OpenAPI documents are in [Part 1 evidence](../../test-reports/m10/part-1/README.md). No real tracking, pricing, authenticated RBAC, event-consumption, or browser demonstration was performed in Part 1.

## Schema baseline

| Migration | Existing content | Observed database history |
|---|---|---|
| `V1__init_analytics_schema.sql` | Creates `operational_sla_snapshots` base table | Version 1, successful |
| `V2__expand_analytics_schema.sql` | Expands snapshot metrics; creates courier metrics, insights, and tool audits | Version 2, successful |

Application tables: `operational_sla_snapshots`, `courier_performance_metrics`, `ai_delivery_insights`, `ai_tool_invocation_audit`. Flyway also owns `flyway_schema_history`.

Live startup validated both migrations; current schema version was 2, with no migration necessary. After the failing KPI read, snapshots and courier metrics both contained zero rows. This is an existing database, not a fresh-install migration test.

Do not edit V1 or V2. Recheck latest `dev` migration versions before assigning the next version. Schema changes belong to later parts.

## Existing defects, separate from new requirements

| ID | Existing defect and evidence | Consequence | Planned part |
|---|---|---|---|
| B01 | KPI read calls `seedInitialSnapshots` inside `@Transactional(readOnly = true)`; live PostgreSQL returned 400 | Empty reporting fails; samples would misrepresent operations if insertion were allowed | 3 |
| B02 | `recordDeliveryEvent` increments `totalParcels` for every event | One parcel's lifecycle inflates parcel counts | 2-3 |
| B03 | No processed-event table, unique marker, or per-order state | Duplicate delivery repeats side effects; concurrent read-modify-save can lose updates | 2-3 |
| B04 | `LocalDate.now()` used instead of event occurrence date | Delayed events and server timezone change daily totals | 3 |
| B05 | Delivered increments `deliveredOnTime`; fixed 38.50 transit fallback; no deadline comparison | Delivery success is mislabeled as SLA compliance | 3, 8 |
| B06 | Fixed 12 active couriers, 45.00 transit fallback, sample district figures | Empty data can appear as real activity | 3 |
| B07 | Assignments increment a lifetime total and mark BUSY; no order correlation/reassignment/completion handling | Assignment count cannot represent current workload or courier outcomes | 3 |
| B08 | Java omits quorum/TTL while broker defines them; live `PRECONDITION_FAILED` on `x-queue-type` | HTTP can work while consumer startup fails | 2 |
| B09 | Consumer only dispatches status/assignment heuristics; neither broker nor Java binds `order.created` to analytics | Cannot reliably count created orders | 2-3 |
| B10 | Shared records supply random IDs/default timestamps when absent | Missing producer identity/time can be hidden; redelivery deduplication becomes unreliable | 2, shared handoff |
| B11 | `OrderLookupTool` fabricates `IN_TRANSIT` for any nonblank ID; keyword agent invents operational assertions | Assistant output is not evidence of tracking or model integration | 5-7 |
| B12 | Raw prompts/context/results persisted; JSON persona accepted; no analytics controller RBAC | Unsafe audit content and missing operational authorization | 4, 6 |
| B13 | Broad runtime handler returns 400 with internal exception message | Server/storage errors look like client errors and leak internals | 4, 6 |
| B14 | Pricing estimate exists in Java but is missing from the static pricing OpenAPI document | Static-spec generation cannot describe the assistant's planned pricing dependency | 5, M4 handoff |

New requirements: daily reports with validated ranges; safe tool contracts and real clients; bounded chat orchestration; provider selection; per-user limits; dashboard charts; conversation widget; integration-test infrastructure and submission evidence. These are planned additions, not existing regressions.

## Test baseline and limitations

Analytics has six test classes and 27 tests. All passed; shared contracts added one passing test in the reactor. The analytics module has no Testcontainers dependency or active Failsafe declaration; the parent's Failsafe configuration is in plugin management. These tests do not establish broker delivery, database concurrency, or gateway authorization.

An existing test explicitly expects sample insertion and 12 active couriers on empty data. Passing that test preserves current behavior; it does not establish correct business semantics. Later fixes must replace those expectations with agreed empty-data requirements.

Frontend `lint` is TypeScript checking, not ESLint: `tsc ... --noEmit` for app and node configs. It passed. Frontend build passed, with the existing minified chunk warning above 500 kB. No new automated tests were necessary for this documentation-only part.
