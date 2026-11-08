# Member 10 integration handoffs

Prepared on 2026-11-08 from commit `531f125dd485bda777d88a54f66e960839218c81`. These are review-ready requests, not messages sent or approvals received. Record owner confirmation and a PR/commit link in the final column. No external messages were sent during Part 1.

Shared ownership is explicitly assigned in `docs/member-guides/README.md`: M9 owns shared event records and RabbitMQ definitions even though the M9 business service is notifications. M1 owns gateway, shared response wrappers, parent POM, and shared frontend/deployment files. Ask the named owner before coordinating shared-file implementation.

| ID / owner | Verified starting interface | Requested agreement / concrete action | Needed by | Status / confirmation |
|---|---|---|---|---|
| H01 / M1 | Gateway routes analytics and legacy aliases; no `/api/v1/ai/**` | Add authenticated route `/api/v1/ai/**` to analytics; keep existing aliases/OpenAPI route; confirm auth and rate-limit behavior | Part 6 | Pending; no owner confirmation recorded |
| H02 / M1 + M2 | Gateway strips external identity headers and reinjects verified `X-User-Id`, `X-User-Role`, merchant/district; constants exist; no `CallerContext` helper | Confirm trusted downstream boundary, role parsing/helper or explicit header fallback, direct-access protection, and forwarding of bearer/correlation headers | Part 4 | Pending |
| H03 / M1 | Public tracking DTO has tracking number, current public status, districts, timeline; 404 on missing data; no response envelope | Confirm canonical path, tracking-number validation formats, unknown vs upstream-unavailable errors, and public safe-field guarantees | Part 5 | Source verified; owner confirmation pending |
| H04 / M4 | Estimate accepts required pickup/delivery district and weight; optional dimensions/speed; response includes estimatedFee/currency; estimate is missing from static pricing OpenAPI | Publish estimate operation/schemas in the pricing spec; confirm examples, city-to-district mapping, missing-origin clarification, STANDARD default, omitted dimensions, and assistant's proposed 50 kg maximum | Part 5 | Source verified; spec synchronization and policy confirmation pending |
| H05 / M5 | `CourierAssignedEvent` has orderId, courierId, assignedAt, timestamp; no tracking number/merchant ID in current record | Confirm reassignment timing, cancellation/unassignment events, workload definition, terminal cleanup, and outcome attribution to effective courier | Part 3 | Pending |
| H06 / M6 | Public FAILED is terminal; current delivery FSM cannot leave FAILED; internal FAILED_ATTEMPT maps to it | Confirm final failure vs retry, cancellation/return exclusion from success denominator, correction/reopening policy, timestamps/order guarantees, and equal-time conflicts | Part 3 | Pending; retry semantics must be settled before implementing outcome counts |
| H07 / M9 + M3/M5/M6 producers | Analytics bindings only `parcel.status.changed.#`, `dispatch.#`; constructors generate missing IDs/times | Add analytics `order.created` binding in broker and Java; guarantee stable producer IDs/times on redelivery; preserve correlation IDs; define envelope/type discrimination and unsupported-message policy | Part 2 | Pending |
| H08 / M9 | Broker analytics queue quorum, TTL 86400000 ms, DLX/routing key; DLQ quorum, TTL 604800000 ms; Java omits quorum/TTL | Align declarations with definitions and agree bounded retries/rejection/DLQ policy; preserve existing messages/volumes during rollout | Part 2 | Runtime mismatch reproduced; fix pending |
| H09 / M1 + M2 | Shell mounts empty `AiAssistantSlot`; frontend has existing dock/panel and auth store | Confirm M10 dock/slot ownership, shell integration, clearing conversation on account change, and shared dependency review if charts need a package | Parts 8-9 | Pending |
| H10 / Team | No actual LLM client; AiModelConfig empty | Choose provider/model, key provisioning, timeout/cost constraints; offline mock default; automated tests require no paid credentials | Part 7 | Pending; no provider/key assumed |
| H11 / M1 + reviewers | Parent manages Failsafe; analytics module does not activate it or include containers | Review required module test dependencies/plugin activation and shared changes if any | Part 2 | Pending |

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
