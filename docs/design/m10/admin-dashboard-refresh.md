# Admin dashboard refresh — 15 November 2026

## Review and design decisions

The previous screens gave large cards to short descriptions, used a monospace face for metric values, offered role changes directly in table rows, and executed account-status actions immediately. Several header controls had no useful action on admin pages. Outcome and courier panels were empty because the demo did not run dispatch/delivery; running the real services also exposed missing route context in delivery events.

Research used primary product/design documentation:

- [Onfleet analytics](https://support.onfleet.com/hc/en-us/articles/360023910431-Dashboard-Analytics): historical task/driver analysis with explicit date and performance context. Applied as date presets, historical outcome cards and a separate current-workload view.
- [Bringg KPI dashboard](https://help.bringg.com/docs/keep-track-of-kpis-with-the-analytics-dashboard): put important KPIs first and filter to relevant time frames. Applied as four readable metric cards followed by trends, outcome distribution and courier workload. Dashboard customization and period comparison were not added because the current API does not supply comparison data.
- [Carbon data tables](https://v10.carbondesignsystem.com/components/data-table/usage/): structured filters, clear row actions and readable account details. Applied as a compact directory with search, role/status filters, protected role badges and labeled actions.
- [Carbon confirmation dialogs](https://www.carbondesignsystem.com/building-blocks/core/components/modal/guidelines): explain the consequences of significant actions. Applied as account-specific suspend/reactivate dialogs, cancel, pending states, inline failure/retry and success announcements.

The visual treatment uses SwiftDrop's existing typography, compact density, Button, SurfaceCard, Modal and semantic delivery colors. New dependencies, external fonts and invented performance statistics were not introduced.

## Implemented behavior

- The application no longer mounts the development persona strip. The admin sidebar has a branded header, role-only subtitle, stronger active navigation and a compact account footer. Developer catalog navigation is hidden for real accounts.
- Admin/dispatcher top bars hide the search, language, assistant and notification controls shown in the request. Other personas keep their existing controls.
- Analytics has 7/30-day presets, custom validated 1–90-day ranges, refreshed-at information, four metric cards, a multi-line trend chart with circular point markers, a light grid and a dashed failed series, outcome doughnut, and sorted courier workload bars with courier names when available. Counts remain available in an accessible table; unknown success rates show an em dash. Workload remains current rather than date-filtered.
- User management has debounced search, role/status filters, consistent status/role badges, pagination, self-account protection, and one status action per account (Suspend for active, Reactivate for inactive), with confirmation before each change. Successful updates invalidate the account queries so counts/filters stay consistent; failures stay visible in the dialog.
- Account roles are fixed at creation on the server. The compatibility role endpoint returns 409 for a different role and 200 for the same role without modifying the account or revoking its sessions. The IAM specification and generated frontend declarations were synchronized.
- Shared modal behavior now traps Tab navigation and restores focus when closed.
- The control-tower SLA banner reports unavailable metrics explicitly instead of substituting 96.5% and zero breaches.

## Real dispatch/delivery integration repair

When the actual services were started, delivery synthesized tracking numbers and emitted status events with null districts. Analytics correctly rejected these payloads, preventing the outcome charts from updating.

`CourierAssignedEvent` now includes optional `pickupDistrict` and `deliveryDistrict`. Automatic/manual dispatch publishers supply the fields from their own assignment record. Delivery stores those existing route fields and uses the supplied order tracking number before publishing status changes. Existing 8/10-argument constructors remain available; older JSON consumers ignore the additive fields. No new tables, migrations, queues or routing keys were needed.

Deploy the updated dispatch and delivery services together to supply valid route context. Shared contracts were rebuilt, producer/consumer regressions were added, and the whole backend was compiled for compatibility.

## Runtime and validation

The local demo retains gateway, IAM, orders, pricing, analytics, dispatch, delivery, the frontend and their required infrastructure. Dispatch/delivery use their own databases and the same dedicated demo RabbitMQ virtual host as analytics. IAM was restarted with the fixed-role policy. Runtime process IDs and database/vhost information remain in the ignored `backend/analytics-ai-service/target-ai/frontend-demo/session.json`.

Synthetic demo parcels were created through the real customer order API, assigned through dispatch and advanced through the courier delivery API. Delivered parcels went through proof upload and OTP verification; failed parcels supplied a failure reason. One parcel per rehearsal remains assigned. The dashboard now shows actual persisted/event-derived demo outcomes and workload.

Eleven rejected events from the first synthetic rehearsal were replayed with their actual order tracking/route data, preserving event IDs and timestamps. Original DLQ evidence was retained. No ordinary customer events or database tables were edited directly.

Validation evidence is in [the refresh report](../../test-reports/m10/admin-ui-refresh/README.md). Local frontend: [Analytics](http://localhost:5173/ops/analytics), [Users](http://localhost:5173/ops/users). Sign in with the demo administrator from the existing local demo instructions.

## Limits

SLA measurement still needs its own real business inputs; the UI now states this honestly. The operational delay predictor remains an advisory calculation using submitted inputs. The existing multi-replica conversation/rate-limit limitation is outside this UI change. Vite retains its large-bundle advisory. No push, PR merge or cluster deployment was performed.
