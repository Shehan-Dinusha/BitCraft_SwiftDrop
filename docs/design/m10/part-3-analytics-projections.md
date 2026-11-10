# Part 3: Correct analytics projections

Implemented on `Buddhika/feat/analytics-projections`, starting at `a770285`. This delivers the member guide's daily/courier projection work and extends the reliability tests. New date-filtered HTTP report endpoints and RBAC remain Part 4.

## What changed and why

| File/component | Responsibility |
|---|---|
| `V4__add_order_and_daily_projections.sql` | Adds per-order state, daily counts, courier daily counts, known couriers, distinct order/courier relationships, and an inbox catch-up flag. V1-V3 are unchanged. |
| `domain/projection/OrderProjection` | One order's creation, latest state, assignment, outcome, attribution, and conflict evidence; explicit Colombo reporting zone. |
| `domain/projection/OrderProjectionReducer` | Reconciles all accepted facts for one order by event time instead of arrival order. Distinct event IDs cannot multiply the same order outcome. |
| `domain/repository/AnalyticsProjectionRepository` | Persists state and applies old-to-new count differences in the same transaction. |
| `service/AnalyticsProjectionService` | Reconciles an order and catches up pending inbox records in batches of 100 orders. |
| `config/AnalyticsProjectionStartup` | Starts inbox catch-up automatically and supplies a testable clock. |
| `service/TelemetryProcessingService` | Keeps Part 2's event registration and all new projection effects inside one database transaction. |
| `service/AnalyticsReportService` | Read-only daily rows, outcome rates, workload, district and courier compatibility reads. No new HTTP routes. |
| `service/SlaMetricsAggregationService` | Existing endpoint adapter now reads real projections; sample insertion and invented defaults removed. |
| Existing KPI/courier DTOs, analytics OpenAPI, generated analytics types | Explicitly represent unavailable measurements as nullable/deprecated fields and document corrected meanings. |
| `TelemetryConsumptionIT` and service/controller tests | Verify exact totals, business duplicates, delayed events, concurrency, migration, catch-up, empty reads, and null response values. |
| `fixtures/publish-part3-demo.ps1` | Explicit synthetic fixture publisher; never runs automatically and only accepts a named Part 3 demo vhost. |

Java paths are relative to `backend/analytics-ai-service/src/main/java/com/swiftdrop/analytics/`.

## Metrics actually implemented

- `created`: distinct orders with an authoritative `order.created`, bucketed by its occurrence date. A CREATED status alone does not invent an order-creation fact.
- `delivered` / `failed`: one unambiguous final delivered-or-failed outcome per order, on the first occurrence of that outcome.
- Success rate: delivered / (delivered + failed), multiplied by 100 and rounded HALF_UP to two decimal places. No outcomes means a null rate.
- Cancellations and standalone returns are excluded from that denominator. A return after failure does not create another completed outcome.
- Workload: current assigned, nonterminal, unconflicted orders. Reassignment removes the old courier's contribution and adds the new one; completion removes it.
- Courier outcome credit: latest assignment effective at or before outcome time. An assignment received late can repair attribution without changing the global delivery count.
- Lifetime courier `totalAssigned`: distinct orders ever observed assigned to that courier, not the number of assignment messages.

This uses the current shared FSM/enum rule that FAILED is terminal. The historical name FAILED_ATTEMPT does not establish a supported reopening workflow. A later nonterminal state after a terminal one is flagged for reconciliation, not silently reopened. M5/M6 policy confirmation remains pending; no team approval is claimed.

## Time and ordering

Canonical inbox payloads retain source instants. Projection timestamps are truncated to PostgreSQL microsecond precision, avoiding rounding a value immediately before midnight into the next date. Daily dates use `Asia/Colombo` explicitly, regardless of the server timezone.

Example: `2026-11-07T18:29:59.999999999Z` belongs to Colombo Nov 7; `2026-11-07T18:30:00Z` belongs to Nov 8. Receipt on a later date does not move either event.

The reducer re-evaluates one order's immutable event history whenever a new event arrives. It therefore handles a status received before an assignment, an older status received after delivery, and distinct IDs repeating the same status. Delivery totals are not derived from the number of messages.

Equal-time contradictory statuses/districts, competing couriers at the same assignment time, or incompatible terminal histories set `conflict_reason` on the order. Conflicted orders retain their source evidence and genuine creation count, but are excluded from outcome/workload counts until a verified correction policy is supplied. This can decrease a previously counted outcome when contradictory evidence arrives. No arbitrary UUID ordering chooses the winner.

## Storage and concurrency

| Table | Meaning |
|---|---|
| `processed_telemetry_events` | Durable Part 2 inbox; new `projection_applied` flag supports catch-up |
| `analytics_order_projection` | One reconciled row per order, including assignment/outcome times and conflict evidence |
| `daily_delivery_stats` | Created/delivered/failed counts keyed by Colombo report date |
| `courier_daily_stats` | Delivered/failed outcomes by outcome date; current active contributions bucketed by effective assignment date |
| `analytics_couriers` | Couriers actually observed in assignment events; no invented fleet size |
| `analytics_order_couriers` | Distinct internal analytics order/courier relationships for assignment totals |

`courier_daily_stats.active` is **not a historical end-of-day snapshot**. Sum active across all dates to obtain current workload. A completion today can remove an active contribution from yesterday's assignment-date bucket. Availability/shift status cannot be inferred from zero workload.

All projection writers acquire the same PostgreSQL transaction advisory lock before registering new events or replaying a batch. This serializes live writers and catch-up across service instances, avoids missing increments and inconsistent attribution, and releases on rollback/commit. Counter updates apply signed deltas atomically and have nonnegative constraints.

The deliberate tradeoff is serialized projection writes and replaying an order's history. This is a correctness-first implementation for the project dataset, not a throughput benchmark. Partitioned/per-order locking would require a separate design for aggregate lock ordering. Avoid mixed-version deployment where old Part 2 replicas continue writing after the final catch-up: upgrade all analytics writers together.

## Upgrade and historical data

On startup, pending accepted inbox facts are reconciled in transactions of up to 100 orders. A live event also reconciles all retained history for its order. Catch-up marks the corresponding inbox rows only when the projections commit. Restarting does not double-count completed catch-up work.

Legacy `operational_sla_snapshots` and `courier_performance_metrics` tables remain for inspection; their data, including old samples, is not trusted as source facts and is not read by the normal reporting service. No destructive cleanup migration was added. Historical activity predating the reliable inbox cannot be reconstructed from aggregate samples.

Do not delete inbox records or truncate materialized tables independently while keeping their flags. A complete rebuild/reset policy is not exposed as a public endpoint in this part.

## Existing HTTP compatibility

Paths and envelopes are retained. Existing `/analytics/kpis` reports today's distinct created orders as `totalParcels`, today's final failures, known couriers with active work, and district breakdowns. Those flows need not match: a parcel created yesterday can be delivered today.

No SLA deadline, agreed transit interval, or rating event exists. Therefore delivered-on-time counts, SLA breaches/rates, transit averages, and customer ratings return **JSON null** rather than synthetic figures. Their schema annotations/static specification/generated types now describe this. `currentDistrict` may also be null; it is a last observed parcel district, not a courier's registered operating district.

Courier status is `BUSY` or `NO_ACTIVE_ORDERS`, not a claim that the courier is on shift. `lastEventAt` is documented as the latest observed assignment business time.

The existing POST `/analytics/snapshots/aggregate` returns the current read model; it does not seed or rebuild data. This behavior is documented in its operation summary/message.

These compatibility corrections need frontend/member review. The new dashboard is still pending, and existing Fleet Control Tower SLA display fallbacks are not changed in this part; do not use those placeholder UI percentages as projection evidence. Part 4 supplies the dedicated daily/rate/workload HTTP contracts; Part 8 supplies the charts.

## Controlled dataset and acceptance checks

Four created orders: A/B delivered by C1, C failed under C2, D reassigned C1 -> C2 and still in transit. Expected: created 4, delivered 2, failed 1, completed 3, success 66.67%, failure 33.33%, C1 active 0, C2 active 1. Replaying all events does not change these totals. Another delivered event ID for A also does not add a delivery.

Container tests cover that dataset, different-order concurrency, delayed attribution, completion, cancellation/return policy, midnight/nanosecond boundaries, visible conflicts, empty reads, weighted range rates, V2 and V3 upgrades, and startup replay. Part 2 duplicate/rollback/retry/DLQ tests now exercise the new projection tables rather than the abandoned legacy counters.

See [the self-verification and commit guide](part-3-self-verification.md) for commands you run yourself. Raw reports/logs remain local under ignored build directories or `.log` files; keep curated test/coverage evidence for final submission as required by the member guide.

## Verification recorded on 2026-11-08

- Clean Maven verify: **43 analytics unit/controller/config tests + 23 integration tests**, zero failures/errors/skips; shared contracts also passed its 1 test. Fresh V1-V4, V2 upgrade, V3 inbox preservation, catch-up and restart were exercised in disposable containers.
- `npm.cmd run api:sync:specs` completed. Unrelated regenerated service files were reverted; only the analytics generated declaration is retained.
- Frontend lint and production build passed. The existing bundle-size warning remains; the dashboard itself is still pending.
- Live packaged analytics on isolated port 18089 returned health UP, empty real KPI counts, and explicit null measurements. Its live OpenAPI exposes the nullable schema.
- The fixture was published twice to temporary storage. SQL and existing HTTP reads agreed: 4 created, 2 delivered, 1 failed, C1 active 0, C2 active 1; accepted IDs 15, pending projections 0.
- A temporary gateway on port 18080 served the updated analytics OpenAPI with 200 and rejected a no-token KPI request with 401. No authenticated RBAC claim is made; that remains Part 4.
- No everyday Compose service/database was started or changed for the live check. Dedicated disposable containers and separate application ports were used. No commit, push, merge, external message, or owner approval was performed.
- Current clean JaCoCo report: service package 148 covered / 159 executable lines (93.08%); controller package 18/18 (100%). These are package line coverage figures, not a claim of complete behavioral coverage.
- Temporary smoke-test Java processes were stopped and their disposable containers removed. Normal development resources and volumes were preserved. New Markdown links/code fences and `git diff --check` were also validated.
