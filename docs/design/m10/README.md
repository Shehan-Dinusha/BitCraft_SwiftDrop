# Member 10: Analytics and AI

Part 1 establishes the starting evidence and intended contracts before changing application behavior.

| Document | Purpose |
|---|---|
| [Implementation roadmap](implementation-roadmap.md) | Ten required implementation parts and optional MCP work |
| [Baseline notes](baseline-notes.md) | Current code, startup procedure, endpoints, schema, and existing defects |
| [Reporting contracts](reporting-contracts.md) | Metric definitions, timezone, proposed API responses, and acceptance examples |
| [Handoff requests](handoff-requests.md) | Verified interfaces and decisions requiring teammates' confirmation |
| [Part 1 self-verification and viva guide](part-1-self-verification.md) | Commands you run yourself, expected observations, and explanations |
| [Part 2 event reliability](part-2-event-reliability.md) | Inbox transaction, raw-message validation, bounded retries, broker parity, and tests |
| [Part 2 self-verification and viva guide](part-2-self-verification.md) | Automated acceptance checks and an optional isolated broker/database demo |
| [Part 3 analytics projections](part-3-analytics-projections.md) | Distinct-order metrics, event-time reconciliation, workload, catch-up, and compatibility changes |
| [Part 3 self-verification and commit guide](part-3-self-verification.md) | Commands, repeatable demo, expected SQL/API results, commit groups, and viva/team summary |
| [Part 4 reporting APIs](part-4-reporting-api.md) | Public report DTOs, inclusive dates, operational authorization, compatibility and viva/team explanation |
| [Part 4 self-verification and commit guide](part-4-self-verification.md) | Real-token gateway checks, controlled SQL/API comparison and commit instructions |
| [Part 4 implementation evidence](../../test-reports/m10/part-4/README.md) | Executed automated/HTTP checks and explicitly pending real-account gateway checks |
| [Part 1 evidence](../../test-reports/m10/part-1/README.md) | Actual check results and captured runtime artifacts |

Part 1 documentation and baseline checks were completed on 2026-11-08. Existing runtime defects remain intentionally recorded for their planned implementation parts. Cross-member decisions are proposals until their owners confirm them; this documentation does not claim team approval.

No application Java, TypeScript, migration, broker, deployment, or published API contract was changed in Part 1. Proposed future responses belong in the design document until implemented and synchronized with OpenAPI and generated types.
