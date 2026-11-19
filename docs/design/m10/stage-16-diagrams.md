# Member 10 Stage 16: design deliverables

Implemented on 12 November 2026 from the current analytics implementation after
Stages 12–15. Sources and exports are committed locally and ready for review and
submission. Push and sharing with the team's document owner remain separate actions.

## Deliverables

| Diagram | Editable source | Vector | Image | PDF |
|---|---|---|---|---|
| Customer AI tool-calling sequence | [Mermaid](ai-tool-calling-sequence.mmd) | [SVG](ai-tool-calling-sequence.svg) | [PNG](ai-tool-calling-sequence.png) | [PDF](ai-tool-calling-sequence.pdf) |
| Complete analytics ERD | [Mermaid](analytics-erd.mmd) | [SVG](analytics-erd.svg) | [PNG](analytics-erd.png) | [PDF](analytics-erd.pdf) |

The PDFs fit each whole diagram on a custom-sized page; use their vector zoom
when reading small labels. The PNGs are high-resolution previews. SVG and Mermaid
sources are suitable for the team's final design document workflow. No Figma
account, uploaded diagram, or online renderer is required.

## What the sequence shows

The authenticated customer's widget sends `POST /api/v1/ai/chat` through the
gateway. The gateway verifies the token and forwards trusted identity headers.
`AiChatService` requires CUSTOMER role, validates the message, consumes the shared
chat/MCP budget, and acquires a conversation belonging to that user. Redacted
history is held in memory, expires after 30 minutes of inactivity, and retains at
most eight user/assistant pairs. It is not a database table.

The model boundary is `LlmClient`: `MockLlmClient` runs locally, while
`OpenAiCompatibleLlmClient` supplies tool schemas and uses the configured real
provider. A model requests tools; the server validates and executes them. The
provider does not receive the caller's JWT or direct database access. The flow
permits at most three tool executions per chat request, across model turns.

- `track_parcel`: use the caller's token through the gateway to retrieve the
  protected order, compare its `customerId` to the caller, and only then retrieve
  `/api/v1/public/track/{number}`. Unknown and foreign parcels yield the same
  `NOT_FOUND` outcome. Only the tracking number, typed public status and recognized
  district fields are returned to the model; protected order data and arbitrary
  upstream text are excluded.
- `calculate_estimate`: send validated weight and districts through the gateway
  to `/api/v1/pricing/estimate`. It requests STANDARD speed with actual weight,
  validates the nonnegative LKR fee and selects a safe result. Origin, destination
  and weight must be supplied; the system does not invent missing values.
- Each attempted execution writes a metadata-only audit before releasing its
  result. Unknown tools are refused and invalid arguments never reach HTTP
  clients. An audit persistence failure stops the request. Private-data requests,
  provider failure and call-limit exits have their own chat audit outcomes.

The response carries the redacted reply, conversation ID and safe tool-use labels.
The legacy chat aliases enter the same guarded orchestration. Operational
prediction and audit pages remain separate and require an operational role.

MCP is an additional entry point, not a hidden hop in the customer widget:
native client → gateway `/api/v1/ai/mcp` → `AiMcpController` → the same
`ToolExecutor` and clients. It shares the caller budget and auditing but invokes
tools directly, without an LLM loop or chat conversation allocation.

## What the ERD means

The snapshot covers **11 tables and 97 columns**: ten application tables plus
`flyway_schema_history`. It was checked against the running
`swiftdrop_analytics.public` PostgreSQL catalog after migrations V1–V5. Exact
nullability, PK/UK markers, type precision and varchar lengths are visible in the
ERD. The [schema dictionary](analytics-schema.md) and
[machine-readable snapshot](analytics-schema.json) additionally record defaults,
check constraints and indexes. Migration hashes make future drift detectable.

There are **no SQL foreign keys** in this schema. Every dashed connector is a
logical identifier association maintained by projection code. Composite primary
keys are marked on each participating column; marking both columns PK does not
make either column individually unique. An absent edge does not imply a missing
constraint: standalone tables are deliberately left unconnected.

| Area | Tables and meaning |
|---|---|
| Event inbox | `processed_telemetry_events`: event ID deduplication and canonical replay payloads |
| Order state | `analytics_order_projection`: reduced state/assignment/outcome and conflict marker |
| Daily totals | `daily_delivery_stats`: creations and outcomes on their own Colombo occurrence dates |
| Courier projections | `analytics_couriers`, `analytics_order_couriers`, `courier_daily_stats` |
| Operational AI | `ai_delivery_insights`: stored delay-risk predictions |
| Audit | `ai_tool_invocation_audit`: caller/tool/outcome/correlation/duration metadata |
| Legacy retained storage | `operational_sla_snapshots`, `courier_performance_metrics`: preserved, but current report reads use projections |
| Schema management | `flyway_schema_history`: technical migration metadata, not a domain entity |

The event consumer registers an inbox row and applies projection changes in one
analytics database transaction. `AnalyticsProjectionRepository` takes a
transaction advisory lock, replays each affected order's retained canonical
events, replaces its reduced state, applies aggregate deltas and marks inbox rows
as projected. Daily totals are derived aggregates, so the ERD does not invent
one-to-one or foreign-key relationships to orders. `courier_daily_stats.active`
is current workload bucketed by effective assignment date; summing all dates
gives current workload. It is not a historical end-of-day snapshot.

Order/courier UUIDs, actor user IDs, districts and tracking numbers may refer to
external domain concepts, but analytics never reads another service's database
and the ERD contains no external service tables or cross-service constraints.
`outcome_courier_id` is a historical logical reference; only current courier
assignment is drawn as an edge to keep the overview legible.

The audit table retains nullable `prompt_or_query`, `input_parameters` and
`output_result` columns for compatibility. Current code leaves prompts and
arguments null; `output_result` can hold a safe outcome code, never raw tool
results or model replies. V5 clears historical raw values. Nullability alone is
not a database-enforced redaction policy; the application controls what is written.

## Sources checked

- [Flyway migrations](../../../backend/analytics-ai-service/src/main/resources/db/migration/)
- [AiChatService](../../../backend/analytics-ai-service/src/main/java/com/swiftdrop/analytics/ai/AiChatService.java)
- [ChatSessionStore](../../../backend/analytics-ai-service/src/main/java/com/swiftdrop/analytics/ai/ChatSessionStore.java)
- [ToolExecutor](../../../backend/analytics-ai-service/src/main/java/com/swiftdrop/analytics/ai/tools/ToolExecutor.java)
- [TrackingClient](../../../backend/analytics-ai-service/src/main/java/com/swiftdrop/analytics/ai/tools/TrackingClient.java)
- [PricingClient](../../../backend/analytics-ai-service/src/main/java/com/swiftdrop/analytics/ai/tools/PricingClient.java)
- [AnalyticsProjectionRepository](../../../backend/analytics-ai-service/src/main/java/com/swiftdrop/analytics/domain/repository/AnalyticsProjectionRepository.java)
- [AnalyticsReportService](../../../backend/analytics-ai-service/src/main/java/com/swiftdrop/analytics/service/AnalyticsReportService.java)
- [API contracts](../../api-specs/analytics-ai-service.yaml)

## Reproduce and validate

Read-only live schema export, when the analytics Postgres container is running:

```powershell
python docs/design/m10/fixtures/export-analytics-schema.py --check
```

To deliberately refresh the snapshot, dictionary and ERD after schema changes,
run that command without `--check`, then review the result before rendering.

Install the pinned renderer in the ignored build directory and supply an installed
Chrome or Chrome headless-shell executable:

```powershell
$env:PUPPETEER_SKIP_DOWNLOAD = 'true'
npm.cmd install --prefix backend/analytics-ai-service/target/diagram-tools --no-save @mermaid-js/mermaid-cli@12.2.0
python docs/design/m10/fixtures/render-diagrams.py --chrome '<absolute-path-to-chrome.exe>'
python docs/design/m10/fixtures/verify-stage16.py
```

The renderer writes SVG, PNG and PDF locally using `mermaid-config.json`. No
frontend dependency or application configuration is changed. The offline check
verifies migration fingerprints, table/column/key/nullability coverage and the
structural integrity of exported files. See [validation evidence](../../test-reports/m10/stage-16/README.md).
Renderer references: [Mermaid CLI](https://github.com/mermaid-js/mermaid-cli) and
[Mermaid ER syntax](https://mermaid.js.org/syntax/entityRelationshipDiagram.html).

## Commit and submission

Stage 16's implementation and exports are committed locally. Its guide's final
criterion also requires sharing the files. No push, PR, or teammate message was
performed for this stage. The commands below document the commit workflow used.
Live Azure verification remains pending;
these diagrams do not turn a protocol-tested adapter into a live-verified provider.

```powershell
git add docs/design/m10/ai-tool-calling-sequence.*
git add docs/design/m10/analytics-erd.*
git add docs/design/m10/analytics-schema.json docs/design/m10/analytics-schema.md
git add docs/design/m10/mermaid-config.json docs/design/m10/stage-16-diagrams.md
git add docs/design/m10/fixtures/export-analytics-schema.py
git add docs/design/m10/fixtures/render-diagrams.py docs/design/m10/fixtures/verify-stage16.py
git add docs/design/m10/README.md docs/member-guides/member-10.md
git add docs/test-reports/m10/stage-16
git diff --cached --stat
git commit -m "docs(analytics): add tool calling sequence and analytics ERD"
```

Share the two PDFs and Mermaid sources with the team's design-document owner.
Include `analytics-schema.md` when the full field dictionary is needed. The source
and SVG copies allow future edits without reconstructing the diagrams from images.
