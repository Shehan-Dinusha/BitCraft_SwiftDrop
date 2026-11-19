# Stages 12–15 validation

Recorded 12 November 2026. All AI checks use the mock provider; no paid model requests.

| Check | Result |
|---|---|
| Analytics unit/controller tests | 108 passed, no failures or skips |
| Analytics RabbitMQ/PostgreSQL integration checks | 26 passed, no failures or skips |
| Service/controller line coverage | 94.51% |
| `mvn -f backend/pom.xml -pl analytics-ai-service -am verify` | Passed |
| `npm.cmd run api:sync:specs` | Passed; retain only the analytics generated-file changes |
| `npm.cmd run lint` | Passed |
| `npm.cmd run build` | Passed; Vite warns about the application bundle size |
| Live gateway/IAM/pricing/orders/analytics acceptance | 56 checks passed |
| Chrome frontend acceptance with fixture API responses | 19 checks passed; no unhandled browser exceptions |

The browser tests use fixture tokens and intercepted HTTP responses to exercise
UI behavior. They do not prove JWT authorization. The separate live suite uses
real IAM-created users, actual gateway JWT verification, real pricing and protected
order APIs, and RabbitMQ projections to verify authorization and ownership.

The live suite lists and calls MCP tools, verifies pricing against the real API,
checks indistinguishable foreign/unknown tracking results, rejects operational
users and browser origins, shares rate limits with chat, and checks report sources
after an actual order-created event. Existing chat security and audit checks also pass.

The browser suite verifies all three charts, applied dates, refresh updates,
empty/error states, contextual follow-ups, close/reopen, retry without duplicate
messages, new conversations, account changes without reloading, mobile overflow,
cooldown controls, and visibility for operational, merchant, courier, dev and signed-out sessions.

## Reproduce

```powershell
$env:DOCKER_HOST = 'npipe:////./pipe/dockerDesktopLinuxEngine'
mvn -f backend/pom.xml -pl analytics-ai-service -am verify
cd frontend
npm.cmd run api:sync:specs
npm.cmd run lint
npm.cmd run build
cd ..
python docs/design/m10/fixtures/verify-ai-assistant.py --build-dir target-ai --analytics-build-dir target
node docs/design/m10/fixtures/verify-frontend.mjs '<path-to-Chrome-or-Chrome-headless-shell>'
```

The live fixture creates and removes only its own random-name Docker containers
and temporary application processes. It preserves existing application data. The
browser fixture starts and stops Vite on port 5187 and its own headless browser.
Both write detailed local results under the analytics build directories.

## Saved artifacts

- `live-result.json`: live check names and count.
- `browser-result.json`: frontend check names, count and browser errors.
- `backend-results.json`: test suite totals and service/controller line coverage.
- `analytics-desktop.png`: dashboard after a fixture report update.
- `assistant-mobile.png`: customer assistant at a 390px viewport above navigation.

Raw service logs, credentials, tokens, full build output and generated HTML reports
remain in ignored build/log directories. JaCoCo HTML is available locally at
`backend/analytics-ai-service/target/site/jacoco/index.html`.

## Current repository limitation

Packaging the current gateway source fails in `TrackingDataSanitizerProxy` because
methods it expects on `InternalTrackingView` are missing. Live checks use the
previously packaged supporting services in `target-ai` and the current analytics
jar in `target`. This is explicitly not a clean full-reactor build claim. Stage 7
live Azure verification remains deferred, and Stage 16 diagrams remain outstanding.
