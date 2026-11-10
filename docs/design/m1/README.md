# Member 1 - Gateway network and real-time documentation

Member 1 documentation deliverable. Both diagrams are committed as a rendered
PNG plus an SVG and the editable Mermaid source.

| Diagram | PNG | Vector | Source |
|---|---|---|---|
| Gateway network architecture | [gateway-network-architecture.png](gateway-network-architecture.png) | [SVG](gateway-network-architecture.svg) | [gateway-network-architecture.mmd](gateway-network-architecture.mmd) |
| WebSocket / SSE handshake | [websocket-sse-handshake.png](websocket-sse-handshake.png) | [SVG](websocket-sse-handshake.svg) | [websocket-sse-handshake.mmd](websocket-sse-handshake.mmd) |

## 1. Gateway network architecture

The architecture diagram shows the request path for every SwiftDrop client:

- **Clients** - the React/Vite SPA on `:5173` and anonymous mobile/shared links
  for `/track/SD-XXXX-XXXX`.
- **Edge** - AKS ingress in production or `localhost:8080` locally.
- **`api-gateway` (`:8080`)** - stateless, owns no database. Ordered web filters
  run first (CORS, security headers, correlation id, internal-route block,
  public-tracking rate limit), then JWT verification and the gateway routes.
- **Real-Time Tracking Hub** - the gateway serves the public tracking API, SSE,
  raw WebSocket, and STOMP from inside the process, so these paths do not go
  through a gateway route.
- **Shared infrastructure** - Redis (public-tracking rate-limit counters and JWT
  blacklist) and RabbitMQ (`swiftdrop.events.exchange`). The gateway queue is
  `swiftdrop.gateway.tracking.queue`, bound with `parcel.status.changed.#`.
- **Downstream services** - the nine business services and their physically
  separate PostgreSQL databases.

Path classes marked on the diagram:

| Class | Paths | Enforcement |
|---|---|---|
| Public | `/api/v1/auth/login`, `register`, `refresh`, `oauth2/*`, `/api/v1/auth/.well-known/jwks.json`, `/api/v1/public/track/**`, `/api/v1/tracking/**`, `/ws/tracking` | No Bearer token. Public tracking is rate-limited per client IP; identity headers are stripped. |
| Protected | All other `/api/v1/**` | Bearer JWT verified with HS256 or RS256; `iss` and `aud` required; verified `X-User-Id`, `X-User-Role`, `X-Merchant-Id`, `X-District-Id` replace any client-supplied values. |
| Internal | `/internal/**` | Blocked at the edge with 404. Only service-to-service calls (for example the gateway to order tracking or merchant API-key validation) may use these paths. |

Security decisions visible in the diagram:

- The JWT filter supports `JWT_ALGORITHM=HS256` (shared secret) and
  `JWT_ALGORITHM=RS256` (IAM JWKS). RS256 resolves keys by `kid`, caches them for
  five minutes, and refetches at most once every five seconds when a token
  presents an unknown `kid`.
- Merchant API-key paths bypass JWT verification and are validated by
  `MerchantApiKeyGlobalFilter`.
- Redis outages fail open for rate limiting and JWT blacklist checks so local
  development and public tracking stay available.

## 2. WebSocket / SSE handshake workflow

The sequence diagram covers the whole live-tracking lifecycle:

1. **First paint** - a public `GET /api/v1/tracking/{id}` through the Redis rate
   limiter and the order-service enrichment lookup.
2. **SSE** - the browser opens
   `GET /api/v1/tracking/{id}/stream`; the gateway sends an initial snapshot and
   a heartbeat comment every 15 seconds.
3. **Event fan-out** - `delivery-execution-service` publishes
   `parcel.status.changed.*`; the gateway consumes it, sanitises PII with
   `TrackingDataSanitizerProxy`, and pushes the sanitised view.
4. **WebSocket / STOMP fallback** - `/ws/tracking` serves STOMP when the client
   requests a STOMP subprotocol and raw JSON otherwise. Private
   `/topic/customer/{customerId}` subscriptions require a verified Bearer token
   (owner or `SYSTEM_ADMIN`). Public `/topic/track/{trackingNumber}` topics are
   anonymous.
5. **Reconnect and degradation** - exponential backoff (1 s, 2 s, 5 s, 10 s),
   SSE to WebSocket failover after two failures, then snapshot polling every
   10 seconds. `404` and `429` are treated as fatal.
6. **Terminal state** - `DELIVERED` or `FAILED` closes the stream and stops
   reconnect/polling.

## Regenerating the rendered files

The sources are plain Mermaid, so they can also be rendered by GitHub or the
Mermaid Live Editor. To regenerate locally with the Mermaid CLI:

```powershell
npx -y @mermaid-js/mermaid-cli -i docs/design/m1/gateway-network-architecture.mmd -o docs/design/m1/gateway-network-architecture.png --size 2400 -b white
npx -y @mermaid-js/mermaid-cli -i docs/design/m1/websocket-sse-handshake.mmd -o docs/design/m1/websocket-sse-handshake.png --size 2000 -b white
```

If the CLI cannot download Chromium, point Puppeteer at an installed Chrome or
Edge executable with `-p puppeteer.json` and an `executablePath` entry.

## Provenance

Generated on 2026-11-10 from `dev` at `bd0ded7` plus the gateway RS256/JWKS
feature branch `Shehan/feat/rs256-jwks-gateway` at `e26b316`. Route paths,
filter order, queue names, heartbeat interval, and reconnect timings were read
from the implementation, not from earlier design notes.
