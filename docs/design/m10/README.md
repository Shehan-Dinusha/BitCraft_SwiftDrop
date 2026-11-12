# Member 10: Analytics and customer AI assistant

Use the [Member 10 guide](../../member-guides/member-10.md) as the scope and stage checklist.
Stages 1–15 are implemented, including MCP tools, dashboard charts and the customer widget.
The chatbot is customer-only; operational reports/audits remain restricted to dispatchers
and administrators. Stage 16 diagram sources, exports and complete schema documentation
are committed locally and ready for review and sharing. Stage 7 live Azure verification remains pending.

| Document | Purpose |
|---|---|
| [Stages 12–15 completion](stages-12-to-15.md) | MCP, chart dependency, dashboard and customer widget |
| [Stage 16 deliverables](stage-16-diagrams.md) | AI sequence and complete analytics ERD; Mermaid, SVG, PNG and PDF |
| [Analytics schema dictionary](analytics-schema.md) | Every column, key, default, check constraint and index |
| [Implementation roadmap](implementation-roadmap.md) | Remaining work, part ordering and dependencies |
| [Stages 1?5 summary](stages-1-to-5-summary.md) | Completed analytics behavior and recorded evidence |
| [AI setup and connection guide](stages-6-to-11-ai-assistant.md) | Mock startup, customer browser checks, real-model settings and limitations |
| [Verification](verification.md) | Consolidated automated/manual checks, SQL reconciliation and review commands |
| [Reporting contracts](reporting-contracts.md) | Metric meanings, timezone, ranges and acceptance examples |
| [Event reliability](part-2-event-reliability.md) | Inbox transactions, validation, bounded retries, broker parity and tests |
| [Analytics projections](part-3-analytics-projections.md) | Distinct-order/daily/courier projections and replay behavior |
| [Reporting APIs](part-4-reporting-api.md) | Report contracts, operational authorization and compatibility |
| [Handoff register](handoff-requests.md) | Cross-member policy and shared-file review dependencies |
| [Historical baseline](baseline-notes.md) | Initial observations, not the current implementation status |
| [Baseline evidence](../../test-reports/m10/part-1/README.md) | Recorded initial results |
| [Reporting evidence](../../test-reports/m10/part-4/README.md) | Recorded reporting and authorization checks |
| [Assistant evidence](../../test-reports/m10/stages-6-to-11/README.md) | Executed backend/frontend checks and pending Azure smoke |

Four overlapping Part 1?4 self-verification documents were consolidated into `verification.md`.
The separate frontend AI demo walkthrough was merged into the AI setup guide. Historical
technical explanations and curated test evidence are retained. Runtime logs, session data,
model keys and build output stay outside commits.
