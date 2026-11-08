# Member 10 integration handoffs

Prepared on 2026-11-08 from commit `531f125dd485bda777d88a54f66e960839218c81`. These are review-ready requests, not messages sent or approvals received. Record owner confirmation and a PR/commit link in the final column. No external messages were sent during Part 1.

Shared ownership is explicitly assigned in `docs/member-guides/README.md`: M9 owns shared event records and RabbitMQ definitions even though the M9 business service is notifications. M1 owns gateway, shared response wrappers, parent POM, and shared frontend/deployment files. Ask the named owner before coordinating shared-file implementation.

| ID / owner | Verified starting interface | Requested agreement / concrete action | Needed by | Status / confirmation |
|---|---|---|---|---|
| H01 / M1 | Gateway routes analytics and legacy aliases; no `/api/v1/ai/**` | Add authenticated route `/api/v1/ai/**` to analytics; keep existing aliases/OpenAPI route; confirm auth and rate-limit behavior | Part 6 | Pending; no owner confirmation recorded |
| H02 / M1 + M2 | Gateway strips external identity headers and reinjects verified `X-User-Id`, `X-User-Role`, merchant/district; constants exist; no `CallerContext` helper | Confirm trusted downstream boundary, role parsing/helper or explicit header fallback, direct-access protection, and forwarding of bearer/correlation headers | Part 4 | Pending |
| H03 / M1 | Public tracking DTO has tracking number, current public status, districts, timeline; 404 on missing data; no response envelope | Confirm canonical path, tracking-number validation formats, unknown vs upstream-unavailable errors, and public safe-field guarantees | Part 5 | Source verified; owner confirmation pending |
| H04 / M4 | Estimate accepts required pickup/delivery district and weight; optional dimensions/speed; response includes estimatedFee/currency; estimate is missing from static pricing OpenAPI | Publish estimate operation/schemas in the pricing spec; confirm examples, city-to-district mapping, missing-origin clarification, STANDARD default, omitted dimensions, and assistant's proposed 50 kg maximum | Part 5 | Source verified; spec synchronization and policy confirmation pending |
| H05 / M5 | `CourierAssignedEvent` has orderId, courierId, assignedAt, timestamp; no tracking number/merchant ID in current record | Review Part 3's effective-time reassignment/workload and outcome attribution; identify cancellation/unassignment events before adding support | Part 3 | Implemented from current assignment contract; owner confirmation pending; no unassignment event invented |
| H06 / M6 | Public FAILED is terminal; current delivery FSM cannot leave FAILED; internal FAILED_ATTEMPT maps to it | Review current terminal-FSM implementation, cancellation/return exclusion, and visible conflict quarantine; confirm retry/reopening/correction policy before changing those semantics | Part 3 | Current code rules implemented and tested; owner confirmation pending; contradictions are retained and excluded, not guessed |
| H07 / M9 + M3/M5/M6 producers | Part 1 bindings omitted `order.created`; constructors generate missing IDs/times | Review Part 2 additive analytics binding and exact-key/raw validation policy; guarantee stable producer IDs/times on republish and preserved correlation IDs | Part 2 | Binding and strict validation implemented on feature branch; M9 review and producer guarantees pending |
| H08 / M9 | Broker analytics queue quorum, TTL 86400000 ms, DLX/routing key; DLQ quorum, TTL 604800000 ms | Review Part 2 Java parity, nonretryable invalid input, and three-attempt storage-failure policy; preserve messages/volumes during rollout | Part 2 | Implemented and exercised with imported definitions in disposable containers; M9 policy/release review pending |
| H09 / M1 + M2 | Shell mounts empty `AiAssistantSlot`; frontend has existing dock/panel and auth store | Confirm M10 dock/slot ownership, shell integration, clearing conversation on account change, and shared dependency review if charts need a package | Parts 8-9 | Pending |
| H10 / Team | No actual LLM client; AiModelConfig empty | Choose provider/model, key provisioning, timeout/cost constraints; offline mock default; automated tests require no paid credentials | Part 7 | Pending; no provider/key assumed |
| H11 / M1 + reviewers | Parent manages Failsafe; Part 2 activates it and adds containers in analytics module only | Review module-scoped integration-test configuration; no parent POM change | Part 2 | Implemented; reviewer confirmation pending |

## Proposed confirmation record

For each handoff, replace Pending only when there is actual evidence:

```text
Handoff ID:
Owner:
Decision and exact request/response or event semantics:
Confirmed date:
PR/commit or recorded review link:
M10 affected part and follow-up tests:
```

## Work that can proceed independently

- Part 2 container-test scaffolding, raw-message validation cases, and duplicate/rollback tests can be developed against explicit fixtures.
- Pricing/tracking client tests can use wire responses matching checked source; policy assumptions must remain documented until owners confirm.
- No direct cross-service database access, invented operational fallback, or missing-origin default is an acceptable substitute for an unresolved interface.
- Missing handoffs do not mean Part 1 evidence is missing. They mean later dependent business behavior is not yet approved.
