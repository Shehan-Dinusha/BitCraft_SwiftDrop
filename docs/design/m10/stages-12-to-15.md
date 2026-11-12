# Member 10: Stages 12–15

Implemented on 12 November 2026 on the existing `Buddhika/feat/frontend`
branch. Changes are committed in stage-based groups; inspect `git log` for the
actual commit history. PR creation and merge remain separate steps.

| Stage | Result |
|---|---|
| 12 | Customer-only MCP tools at gateway `/api/v1/ai/mcp` |
| 13 | Recharts 3.10.1, with a checked-in npm lockfile |
| 14 | `/ops/analytics`, with navigation for dispatchers and system administrators |
| 15 | Existing customer widget hardened and browser-verified |

Stage 7 live Azure verification remains deferred. Stage 16 diagrams remain outside
this change. Mock mode requires no model key.

## MCP connection

Connect a native MCP client supporting Streamable HTTP to
`http://localhost:8080/api/v1/ai/mcp`, supplying an authenticated customer's
`Authorization: Bearer <access-token>` header. MCP protocol version is `2025-03-26`.
The server uses stateless JSON responses, does not issue MCP session IDs, and
returns 405 for GET because it does not provide a server-initiated SSE stream.
All browser `Origin` headers are rejected. There is no OAuth discovery flow;
use a client that supports supplying the existing IAM token explicitly.

Supported methods: `initialize`, `ping`, `tools/list`, `tools/call`.
Notifications return 202 without a response body. Protocol results/errors use
JSON-RPC envelopes rather than the REST `ApiResponse` envelope. HTTP identity,
authorization, rate-limit and capacity failures retain normal service error responses.

PowerShell example, with a token supplied through your terminal environment:

```powershell
$headers = @{
  Authorization = "Bearer $env:CUSTOMER_ACCESS_TOKEN"
  Accept = 'application/json, text/event-stream'
}
$endpoint = 'http://localhost:8080/api/v1/ai/mcp'
$initialize = @{
  jsonrpc = '2.0'; id = 1; method = 'initialize'
  params = @{
    protocolVersion = '2025-03-26'; capabilities = @{}
    clientInfo = @{ name = 'swiftdrop-demo'; version = '1.0' }
  }
} | ConvertTo-Json -Depth 8
Invoke-RestMethod $endpoint -Method Post -Headers $headers -ContentType application/json -Body $initialize

$list = @{ jsonrpc = '2.0'; id = 2; method = 'tools/list'; params = @{} } | ConvertTo-Json
Invoke-RestMethod $endpoint -Method Post -Headers $headers -ContentType application/json -Body $list

$call = @{
  jsonrpc = '2.0'; id = 3; method = 'tools/call'
  params = @{
    name = 'calculate_estimate'
    arguments = @{ weightKg = 2; originCity = 'COLOMBO'; destinationCity = 'KANDY' }
  }
} | ConvertTo-Json -Depth 8
Invoke-RestMethod $endpoint -Method Post -Headers $headers -ContentType application/json -Body $call
```

For tracking, call `track_parcel` with `arguments = @{ trackingNumber = '<your-order-tracking-number>' }`.
Both tools reuse `ToolExecutor`, so schemas, validation, gateway clients,
ownership checks, public-safe results and durable audits are shared with chat.
Chat messages and MCP tool calls share the ten-request per-minute per-user
budget. MCP calls do not allocate chat sessions. Rate limits and conversations
are still in-memory, per service instance, and reset on restart.

## Dashboard and widget

Open `/ops/analytics` as DISPATCHER or SYSTEM_ADMIN using the Analytics navigation
entry. The default range is the last seven Colombo calendar dates, inclusive.
Daily counts and summed outcome rates accept 1–90 inclusive dates. Workload
always shows the current projection, including known idle couriers, and is not
historical workload or online availability. Cancellation and return counts are
excluded from the outcome denominator; no completed outcomes means no rate.

Reports refresh every 15 seconds in an active tab and through Refresh reports.
The page offers accessible numeric alternatives, explicit empty states and retry
feedback while preserving the last successful data. Date changes use separate
query keys and abortable requests. Calls use generated contract types and the
existing gateway API client. No demo numbers are substituted into the product UI.

The customer widget retains its existing CUSTOMER/customer-route/dev-session
checks, shared launcher/panel styling, contextual conversations and account-keyed
mount. New behavior cancels requests on unmount, returns focus to the launcher
on close, retries without duplicate user bubbles, and disables the composer
during a visible 60-second rate-limit cooldown. The shared `AssistantPanel`
`disabled` prop pauses input without pretending the assistant is typing.

## Validation and limits

See [saved evidence](../../test-reports/m10/stages-12-to-15/README.md).

The current gateway source cannot be packaged because its
`TrackingDataSanitizerProxy` expects methods absent from the current
`InternalTrackingView` contract. Live acceptance therefore uses previously
packaged dependency services under `target-ai` with the newly built analytics
jar under `target`. This validates real gateway traffic but does not establish
that the current full reactor builds. The gateway owner needs to reconcile that
separate contract mismatch before a clean full-stack build.

Shared-file review for Member 1: Recharts dependency/lockfile, two Analytics
navigation entries, and the additive `AssistantPanel.disabled` prop. This note
is the handoff; no teammate messages were sent.

## Commit guide

Review `git status` and `git diff` first. Keep your existing branch unless you
intend to create a new one; do not switch to `dev` with this working tree.
Target the PR at `dev`. Stage specific paths, avoiding `.env`, logs, build
directories and other services' generated types.

Suggested commits, splitting the larger stages for review:

1. `feat(ai): expose assistant tools over MCP` — `ChatSessionStore.java`,
   `AiMcpController.java`, `McpRequest.java`, `McpResponse.java`, `AiMcpControllerTest.java`.
2. `docs(ai): synchronize MCP contract and generated types` —
   `docs/api-specs/analytics-ai-service.yaml`,
   `frontend/src/types/generated/analytics-ai-service.ts`.
3. `build(frontend): add chart library` — `frontend/package.json` and `frontend/package-lock.json`.
4. `feat(frontend): add analytics dashboard` — `AnalyticsDashboardPage.tsx`,
   `AppShell.tsx`, and the three new report methods in `analyticsAiApi.ts`.
   Use `git add -p frontend/src/services/api/analyticsAiApi.ts` to stage the report
   methods here and leave the `chat` cancellation change for the next commit.
5. `feat(frontend): harden floating customer AI assistant` —
   `CustomerAiAssistant.tsx`, `AiAssistant.tsx`, the remaining API-client change,
   and `frontend/COMPONENTS.md`.
6. `test(analytics): verify MCP dashboard and customer assistant` — both fixtures
   under `docs/design/m10/fixtures/`, this completion guide, the member-guide
   status entry and saved evidence under `docs/test-reports/m10/stages-12-to-15/`.

For each group, run `git add <listed-paths>`, inspect `git diff --cached`, then
`git commit -m '<message>'`. After reviewing all commits:

```powershell
git push -u origin HEAD
```

Open the PR into `dev`, paste the supplied description, and request Member 1's
review of the small shared-frontend integration changes.

Protocol references: [MCP transport](https://modelcontextprotocol.io/specification/2025-03-26/basic/transports),
[MCP tools](https://modelcontextprotocol.io/specification/2025-03-26/server/tools).
Chart reference: [Recharts ResponsiveContainer](https://recharts.github.io/en-US/api/ResponsiveContainer/).
