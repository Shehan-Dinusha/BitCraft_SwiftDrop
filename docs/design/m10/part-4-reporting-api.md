# Part 4: Authorized reporting APIs

Implemented on `Buddhika/feat/reporting-api`. Part 4 exposes Part 3's persisted projections as reporting APIs. It does not add a new projection, migration, chart, or AI provider.

## What changed and why

| File or area | Responsibility and reason |
|---|---|
| `AnalyticsReportController` | Adds the three planned GET routes, maps internal database read models into public DTOs, and preserves the shared `ApiResponse<T>` envelope. |
| `ReportDateRange` | Binds required ISO `from`/`to` query parameters. Jakarta validation rejects missing dates, reversed dates and ranges exceeding 90 inclusive days before calling the service. |
| `DailyDeliveryReportResponse` | Records the requested dates, `Asia/Colombo` timezone and chronological day rows. Part 3's service fills missing days with zero counts. |
| `DeliverySuccessRateResponse` | Returns delivered, failed and completed counts and percentages. Explicit Jackson inclusion preserves both rate fields as JSON null when no completed outcomes exist. |
| `CourierWorkloadReportResponse` | Returns projection-read time, total current active orders and known couriers, including idle couriers. It exposes IDs and counts without names/contact details. |
| `OperationalReportAccess` | Marks operational controllers or methods requiring verified dispatcher/admin access, and declares OpenAPI bearer security plus 401/403 responses. |
| `ReportingAuthorizationConfig` | Registers an MVC interceptor. Before parameter binding or service calls, it requires a UUID user ID and canonical `DISPATCHER` or `SYSTEM_ADMIN` role from shared gateway identity headers. Missing/malformed identity produces 401; disallowed role produces 403. |
| Existing KPI and AI controllers | Protects operational data and legacy aliases without changing their successful response payloads. |
| `GlobalExceptionHandler` | Preserves authorization status codes, returns shared safe error envelopes with actual path/correlation ID, handles request validation as 400, and hides unexpected runtime/database details behind a generic 500. Previously every runtime exception became 400 and exposed its message. |
| Analytics YAML and generated TypeScript | Documents the new routes, parameters, response schemas, nullable rates and errors; advertises bearer security on protected existing operations. Types were regenerated using the repository script. |
| Tests | Adds 18 reporting/authorization controller cases and two database-backed HTTP cases; supplies verified test identities to existing authorized-controller tests. Existing integration contexts now start a servlet server on a random port. |

## API behavior

| Route under `/api/v1/analytics` | Response `data` |
|---|---|
| `GET /deliveries/daily?from=2026-11-08&to=2026-11-09` | `{from,to,timezone,days:[{date,created,delivered,failed}]}` |
| `GET /success-rate?from=2026-11-08&to=2026-11-09` | `{from,to,timezone,delivered,failed,completed,successRatePercent,failureRatePercent}` |
| `GET /couriers/workload` | `{asOf,totalActiveOrders,couriers:[{courierId,activeOrders}]}` |

Both dates are included: Jan 1–Mar 31, 2026 contains 90 days and is accepted; Jan 1–Apr 1 contains 91 days and is rejected. Dates are business dates in Colombo, not UTC calendar dates. Creation and delivery counts are independent event-time flows: a parcel created yesterday and delivered today appears in different rows for those two facts.

`completed = delivered + failed`; `successRatePercent = 100 × delivered / completed`. Both rates use summed counts across the range and two-decimal HALF_UP rounding. They are not averages of daily percentages or SLA on-time measurements. Cancellations/returns are excluded under Part 3's documented policy. With no outcomes, counts are zero and rates are null. With no workload data, total is zero and couriers is an empty array.

Current workload sums all assignment-date buckets. `asOf` identifies the read time; it does not assert that all messages have arrived or that a courier is online. Known couriers remain visible with zero active orders. Static `/couriers/workload` is verified to coexist with `/couriers/{courierId}`.

## Authorization and compatibility

The request flow is:

```text
Client bearer JWT -> gateway verifies JWT and replaces identity headers
  -> analytics MVC interceptor checks verified user ID and role
  -> validated report parameters -> read-only query -> public record -> ApiResponse
```

New reports require `DISPATCHER` or `SYSTEM_ADMIN`. Existing `/analytics/kpis`, `/analytics/couriers`, `/analytics/couriers/{courierId}`, and `POST /analytics/snapshots/aggregate` now require the same roles. Their `/operational-kpi/...` aliases are protected by the same method annotations. Existing `/analytics/ai/predict-delay`, `/reroute-suggestions` and `/audits`, plus `/ai-delivery-assistant/...` aliases, also require these roles because they expose operational predictions, rerouting or audit data.

The general assistant `/agent-query` and controller health methods were not converted into operational reports. The gateway still requires authentication for their existing service paths. Chat guardrails and audit sanitization remain Part 6. No JSON `callerPersona` grants reporting access. The downstream interceptor accepts canonical role names only; legacy JWT claims are normalized by the gateway's existing `UserRole.fromClaim` behavior.

**Trust boundary:** analytics does not independently verify the JWT. It uses the shared gateway headers, as the current architecture specifies. Direct access to port 8089 with forged identity headers can impersonate a caller if the service port is externally reachable. Keep downstream access internal; M1/M2's network/service-auth agreement H02 remains pending. Missing headers fail closed, but headers alone do not cryptographically prove gateway provenance. Do not present this as protection against an attacker who can bypass the gateway.

No gateway route change was necessary: the existing analytics route already covers all three public paths. No shared DTO/enums, entity, Flyway file, queue, event, or environment variable changed. Existing successful payloads remain compatible; access restrictions and safe 500 handling are intentional behavior changes to communicate to consumers.

## Verification and limits

The final local run passed 61 analytics unit/controller/config tests and 25 real PostgreSQL/RabbitMQ integration tests, with zero failures/errors/skips. The shared-contract module's separate test also passed. Frontend static generation, lint and build passed; the existing large-bundle warning remains nonfatal.

The new HTTP integration checks exercise the running servlet server and real SQL queries, inclusive Colombo boundaries, zero-filled dates, exact counts/rates/workload, authorization rejection, empty-rate serialization, and live bearer-security documentation. Existing 23 integration cases continue proving event reliability, projection calculations, weighted rates and upgrades.

Follow-up verification on 2026-11-10 completed real IAM-account checks through port 8080: 30 acceptance checks passed with isolated services and disposable data. See the [Stages 1–5 completion guide](stages-1-to-5-summary.md) and the consolidated verification guide (the historical stage-5 helper is absent from this checkout). Admin/dispatcher access, customer rejection and forged-header rejection are now verified through the gateway. M5/M6's projection semantics and M1/M2's trusted downstream boundary remain review dependencies.

## Viva questions

| Question | Brief answer |
|---|---|
| What does Part 4 add? | A validated, documented and role-protected HTTP interface over the analytics projections from Part 3. |
| Why record DTOs? | They define immutable public payloads and keep database/internal read models separate from the API contract. |
| Why validate dates twice? | The HTTP boundary gives clients 400 before querying; the existing service also protects internal callers. |
| Why maximum 90 days? | It bounds report work/response size and makes inclusive boundary behavior explicit. |
| Authentication versus authorization? | The gateway verifies who the token represents; analytics checks whether that verified role may read operational reports. |
| Why 401 versus 403? | 401 means missing/unusable identity; 403 means the caller has a role that lacks permission. |
| Can the UI persona establish access? | No. A request field or forged incoming header cannot override the gateway's verified JWT role. Direct downstream ports still require network protection. |
| Why an interceptor? | It checks the mapped controller method before argument processing/service calls, covering aliases through the same annotation without duplicating role checks. |
| Why not average daily rates? | Days have different completed-order counts; summed numerators/denominators give the correct weighted result. |
| Why null instead of zero percent? | With no completed deliveries, the denominator is zero. Null means unavailable, while zero means actual outcomes with no successes. |
| Did Part 4 change the database? | No. It reads Part 3's tables and reuses its metric definitions. |
| What proves it works? | Controller rejection tests, real SQL/HTTP agreement and OpenAPI checks pass; real-account gateway checks must also be performed using the guide. |

## Team update

> Member-guide Stages 1–5 are implemented: reliable events, daily/courier projections, reporting APIs, aggregation tests and operational authorization. All 61 analytics unit/controller/config tests, 25 database/broker integration tests and frontend checks pass. Thirty real-account gateway acceptance checks also passed. The M1/M2 deployment trust-boundary review remains separate. Dashboard charts and the AI assistant remain later work.
