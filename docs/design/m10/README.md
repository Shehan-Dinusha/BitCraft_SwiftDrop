# Member 10: Analytics and customer AI assistant

Start with the [Member 10 scope guide](../../member-guides/member-10.md).
The implementation includes operational analytics, customer-only AI chat, real gateway tools,
MCP, the dashboard/widget and the complete analytics ERD and AI sequence diagrams.
Live Azure pricing and owned-tracking verification is recorded in the Azure verification guide.

| Document | Purpose |
|---|---|
| [Verification](verification.md) | Current build, test, API, browser and data-reconciliation checks |
| [AI setup](stages-6-to-11-ai-assistant.md) | Mock/real providers, gateway tools, customer authorization and conversation limits |
| [Azure setup and costs](azure-setup-and-cost-management.md) | Provisioning, credentials and cost controls |
| [Azure verification](stage-7-azure-verification.md) | Deployment settings and recorded live provider checks |
| [MCP and frontend](stages-12-to-15.md) | MCP connection, dashboard and customer-widget integration |
| [Admin UI and integration changes](admin-dashboard-refresh.md) | Current UI behavior, role policy and dispatch/delivery route-context fix |
| [Reporting contracts](reporting-contracts.md) | Metric definitions, date ranges and workload semantics |
| [Event reliability](part-2-event-reliability.md) | Idempotency, validation, retries and broker declarations |
| [Analytics projections](part-3-analytics-projections.md) | Daily/order/courier projections and reconciliation |
| [Reporting APIs](part-4-reporting-api.md) | Report contracts and operational access control |
| [Diagrams](stage-16-diagrams.md) | Sequence/ERD sources and SVG/PNG/PDF exports |
| [Schema dictionary](analytics-schema.md) | Complete table, column, key and constraint definitions |
| [Shared-file handoffs](handoff-requests.md) | Ownership review dependencies; runtime success is not owner sign-off |
| [Current validation evidence](../../test-reports/m10/admin-ui-refresh/README.md) | Review results and current screenshots |

Historical test reports under `docs/test-reports/m10/` are retained as submission evidence.
The obsolete baseline/roadmap, stage-1–5 summary and duplicate Part 4 self-verification
have been consolidated into this index and `verification.md`. Temporary audit notes and
intermediate screenshots were removed. Keep runtime logs, credentials, tokens, browser
profiles and build outputs in ignored directories.
