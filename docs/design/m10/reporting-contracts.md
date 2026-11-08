# Member 10 reporting and assistant contracts

## Decision status

This is the M10 implementation proposal established in Part 1. `Asia/Colombo`, date rules, and empty-result conventions are the chosen M10 design defaults. Cross-service outcome/retry/assignment rules require the confirmations in [handoff requests](handoff-requests.md). No teammate approval or implemented endpoint is implied. Update this document when a handoff changes a rule, before implementing that rule.

The published current contract remains `docs/api-specs/analytics-ai-service.yaml`. Future endpoints below are design examples, not captured production responses. Add them to OpenAPI and regenerate frontend types in Part 4/6 when their handlers are implemented.

## Time and range rules

- Store source timestamps as UTC `Instant` values; derive reporting dates using `ZoneId.of("Asia/Colombo")` explicitly, never server-default `LocalDate.now()`.
- Use event occurrence time, not consumption time. Missing producer time is invalid for report projection rather than silently assigned to today.
- `from` and `to` are required ISO dates, inclusive. A valid range has `from <= to` and `DAYS.between(from, to) + 1 <= 90`.
- Translate the range into `[from at local midnight, day after to at local midnight)` instants. This avoids ambiguous end-of-day precision.
- Example: Colombo 2026-11-08 starts at `2026-11-07T18:30:00Z`; the instant immediately before belongs to Nov 7, and exactly `18:30:00Z` belongs to Nov 8.
- Return daily rows in ascending date order, including zero rows for dates without activity. Creating those response rows must not insert database rows.
- Report `timestamp` is the UTC response time. It is different from the event occurrence time and report date.

## Metric definitions

| Metric | Proposed precise meaning |
|---|---|
| `created` | Distinct `orderId` values with an accepted `OrderCreatedEvent`, on its occurrence date |
| `delivered` | Distinct orders whose accepted terminal outcome is `DELIVERED`, on that outcome's occurrence date |
| `failed` | Distinct orders whose accepted terminal outcome is `FAILED`, on that outcome's occurrence date; requires M6 confirmation that this is final failure |
| `completed` | `delivered + failed`; a delivery-outcome denominator, not all created orders |
| `successRatePercent` | `100 * delivered / completed`, decimal rounded to two places using HALF_UP; null if completed is zero |
| `failureRatePercent` | `100 * failed / completed`, same rounding; null if completed is zero |
| `activeOrders` | Current distinct nonterminal orders assigned to a courier, not lifetime assignment events |
| `totalActiveOrders` | Sum of current per-courier `activeOrders`; excludes unassigned orders |
| SLA/on-time | Undefined until an actual deadline or approved SLA rule exists; do not substitute success rate |

Created and delivered counts on one date are independent flows. An order created yesterday and delivered today contributes to yesterday's created count and today's delivered count. This is not a creation-cohort conversion report.

`CANCELLED` and `RETURNED_TO_SENDER` remove active workload, but are excluded from the proposed delivered/failed denominator. If a return follows an already recorded final failure, it must not add a second completed outcome. Do not infer final failure from a non-null `failureReason` alone. Confirm this policy with M6/product before Part 3.

Current code evidence: `PublicOrderStatus.isTerminal()` includes `FAILED`, `DELIVERED`, `CANCELLED`, and `RETURNED_TO_SENDER`; the current delivery FSM has no outgoing transitions from FAILED. However, `InternalQueueStatus.FAILED_ATTEMPT` maps to public FAILED. If M6 supports retry/reopening in the future, final failure needs an explicit final-outcome/attempt distinction. Until agreed, do not silently count every failed attempt as a new failed order.

## Event and projection rules

1. Require stable producer `eventId`, business `orderId`, and occurrence time before record defaults obscure missing values. Preserve correlation IDs for tracing.
2. Persist the unique processed-event marker and its projection effects in one transaction. Unique-constraint protection must hold under concurrent delivery. Rollback must remove both effects and marker.
3. Count orders, not events. Distinct new event IDs for repeated unchanged statuses still must not increase business counts.
4. Maintain per-order creation, latest status, assignment, and outcome state in the analytics database. No cross-service database lookup.
5. A later assignment replaces the active courier: subtract from the previous courier's workload and add to the new courier's workload exactly once.
6. Terminal state removes workload. An assignment received after a terminal status does not reopen the order just because it arrived later.
7. Use `assignedAt` as assignment business time; status/order event `timestamp` as their occurrence time. Producer consistency of these timestamps is a handoff requirement.
8. Attribute an outcome to the latest valid assignment effective at or before that outcome time. A delayed qualifying assignment should reconcile attribution without changing the global outcome total. Until an assignment exists, retain the outcome as unattributed rather than inventing a courier.
9. Older events may fill historical facts but must not overwrite a newer current state. Equal-time conflicting states and incompatible terminal outcomes require reconciliation/error evidence, not an arbitrary UUID sort or extra count. Event ordering/version guarantees remain a handoff question.
10. Under the current terminal-FSM proposal, each order has at most one delivered-or-failed outcome. Repeated outcomes are ignored as business duplicates. Explicit corrections/reopening require an agreed correction policy and projection reconciliation.
11. Workload rows include known couriers with zero active orders, sorted by courier ID for deterministic output. Unknown/unseen couriers cannot be inferred from assignment events; do not call the row count the number of all registered or online couriers.

## Proposed reporting API

All three endpoints require a verified `DISPATCHER` or `SYSTEM_ADMIN`. UI persona `ADMIN_OPS` is not an authorization role. Missing/invalid credentials through gateway produce 401; authenticated disallowed roles produce 403. Direct service access needs a trusted network boundary/header policy agreed with M1/M2. A request JSON field cannot establish identity.

Successes use the existing `ApiResponse<T>` envelope. Errors should use shared `ApiErrorResponse`, appropriate status, and a safe message without SQL, tokens, or prompts. Define validation errors for missing/invalid dates, reversed ranges, and ranges longer than 90 inclusive days as 400.

### Daily deliveries

```http
GET /api/v1/analytics/deliveries/daily?from=2026-11-08&to=2026-11-09
```

Illustrative response for the controlled dataset below:

```json
{
  "success": true,
  "message": "Daily delivery report retrieved",
  "data": {
    "from": "2026-11-08",
    "to": "2026-11-09",
    "timezone": "Asia/Colombo",
    "days": [
      { "date": "2026-11-08", "created": 4, "delivered": 2, "failed": 1 },
      { "date": "2026-11-09", "created": 0, "delivered": 0, "failed": 0 }
    ]
  },
  "timestamp": "2026-11-09T06:00:00Z"
}
```

### Success and failure rate

```http
GET /api/v1/analytics/success-rate?from=2026-11-08&to=2026-11-09
```

```json
{
  "success": true,
  "message": "Delivery outcome rates retrieved",
  "data": {
    "from": "2026-11-08",
    "to": "2026-11-09",
    "timezone": "Asia/Colombo",
    "delivered": 2,
    "failed": 1,
    "completed": 3,
    "successRatePercent": 66.67,
    "failureRatePercent": 33.33
  },
  "timestamp": "2026-11-09T06:00:00Z"
}
```

For an empty outcome range, counts are zero and both rates are null. Because shared `ApiResponse` omits null envelope fields but future nested DTO serialization is not defined yet, Part 4 must explicitly preserve the nested rate fields as JSON null and verify that contract in OpenAPI/generated types. The UI displays "No completed deliveries", not 100% success.

Range rates are computed from summed outcome counts, not the unweighted average of daily percentages. For example, 1/1 on day one and 1/9 on day two produce 2/10 = 20%, not 55.56%.

### Current courier workload

```http
GET /api/v1/analytics/couriers/workload
```

```json
{
  "success": true,
  "message": "Current courier workload retrieved",
  "data": {
    "asOf": "2026-11-09T06:00:00Z",
    "totalActiveOrders": 1,
    "couriers": [
      { "courierId": "00000000-0000-0000-0000-000000000101", "activeOrders": 0 },
      { "courierId": "00000000-0000-0000-0000-000000000102", "activeOrders": 1 }
    ]
  },
  "timestamp": "2026-11-09T06:00:00Z"
}
```

This is current event-projected workload, not historical daily workload or authoritative online availability. No `from`/`to` parameters are defined. `asOf` is projection-read time, not a guarantee that all broker messages have arrived. Empty storage returns `couriers: []`, `totalActiveOrders: 0`. Do not include names/contact details unless a specific authorized UI requirement is agreed.

Static `/couriers/workload` must coexist with existing `/couriers/{courierId}` without UUID conversion errors; verify routing in Part 4.

## Controlled acceptance dataset for later parts

All ordinary timestamps below fall on 2026-11-08 in Colombo. These are synthetic design examples; no events were published in Part 1.

| Order | Event sequence | Expected outcome |
|---|---|---|
| A | Created, assigned C1, in transit, delivered | Created +1; delivered +1; C1 active 0 |
| B | Created, assigned C1, delivered | Created +1; delivered +1; C1 active 0 |
| C | Created, assigned C2, final FAILED | Created +1; failed +1; C2 active 0 |
| D | Created, assigned C1, reassigned C2, in transit | Created +1; no completed outcome; C1 active 0, C2 active 1 |

Totals: created 4, delivered 2, failed 1, completed 3, success 66.67%, failure 33.33%, active workload 1. Replay all messages with the same IDs: unchanged totals. Send a new event ID repeating A's delivered state: still unchanged business totals.

Boundary checks: `2026-11-07T18:29:59Z` belongs to Nov 7; `2026-11-07T18:30:00Z` belongs to Nov 8. Deliver an old IN_TRANSIT event after A's DELIVERED event: A remains terminal. Deliver B's qualifying assignment after its outcome: reconcile courier attribution without adding another delivery.

## Assistant integration contracts

Existing `/api/v1/analytics/ai/agent-query` accepts `{query, callerPersona, contextTrackingNumber}` and responds with `{answer, detectedIntent, confidence, suggestedActions, toolInvocations}` inside `ApiResponse`. Keep a compatibility adapter when adding `/api/v1/ai/chat`; do not change the legacy request shape to `{message}` silently. Ignore persona as an authority source.

Proposed chat request: `{ "message": "Where is my parcel?", "conversationId": "optional-owned-id" }`. Proposed data: `{ "reply": "...", "toolCalls": [] }`; exact safe tool-call fields and conversation policy are finalized in Part 6. Maximum three tool calls per request, strict allowlist, validated arguments, safe metadata audit, and authenticated per-user limits apply to mock and real providers alike.

| Tool | Provider contract verified from code | M10 behavior |
|---|---|---|
| `track_parcel` | Gateway `GET /api/v1/tracking/{trackingNumber}` (alias `/api/v1/public/track/{trackingNumber}`); direct `PublicTrackingViewDto`, not `ApiResponse` | Expose tracking ID, public status, districts, and public milestones only; unknown/unavailable stays unknown/unavailable |
| `calculate_estimate` | `POST /api/v1/pricing/estimate`; `ApiResponse<EstimateResponse>` | Supply pickup/delivery district and actual weight; use returned `estimatedFee` and `currency`, not an invented amount |

Pricing request example matching current source:

```json
{
  "pickupDistrict": "COLOMBO",
  "deliveryDistrict": "KANDY",
  "actualWeightKg": 2.00,
  "speed": "STANDARD"
}
```

Current pricing DTO defaults omitted speed to STANDARD. Dimensions are optional but have minimum constraints when present; dimensional billing behavior still needs M4 confirmation. The assistant's proposed 0.01-50 kg validation is stricter than the current DTO, which has a minimum but no 50 kg maximum. Confirm that product limit with M4.

The static pricing OpenAPI currently omits the estimate operation despite its Java implementation. H04 asks M4 to synchronize it; the Java DTOs are the inspected source for this proposal, not a claim that generated pricing types already expose the endpoint.

Do not silently assume Colombo for "How much to send 2 kg to Kandy?" Ask for the origin. Use district enums and agreed city mapping; do not invent a city directory. Preserve `Authorization` where required and `X-Correlation-Id`; set bounded HTTP timeouts. Tool output must omit customer/courier identities, phone numbers, exact addresses, tokens, and internal payout data.
