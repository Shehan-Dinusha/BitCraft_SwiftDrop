# Member 1 - API Gateway test and coverage evidence

Execution date: 2026-11-12. Windows, Java 25.0.5.0.2, Maven 3.9.11. Branch
`Shehan/test/m1-gateway-coverage`, based on `dev` at `c675fe6`. These are
observed results, not a claim that the branch has been merged or accepted.

## Commands and results

| Check | Observed result |
|---|---|
| `mvn -f backend/pom.xml -pl api-gateway -am test` | BUILD SUCCESS. `common-contracts` 46/46 and `api-gateway` 87/87, zero failures, errors, or skips. |
| `mvn -f backend/pom.xml -pl api-gateway -am verify` | BUILD SUCCESS, including `spring-boot:repackage` and the JaCoCo `report` goal. |
| Whole gateway coverage | 81.3% line (766 of 942), 62.8% branch (236 of 376). |
| `service/` package coverage | 94.7% line (142 of 150), 75.0% branch (54 of 72): enrichment 100% (10/10), sanitizer 95.9% (71/74), broadcast hub 92.4% (61/66). |
| `controller/` package coverage | 96.4% line (54 of 56), 87.5% branch (14 of 16). |
| `websocket/` package coverage | 90.9% line (159 of 175), 66.7% branch (64 of 96): STOMP handler 100% (18/18), STOMP session state 93.5% (86/92), token verifier 96.2% (50/52). |

The 70% controller line-coverage target is met (96.4%, up from 48.2%). The
earlier gap was the WebSocket and STOMP session paths, which this branch now
covers. `TrackingWebSocketHandler` is still only partly covered (4 of 11 lines);
it is the legacy handler and is not on the current public path.

## Tests added by this branch

| Test class | Added cases | Proves |
|---|---|---|
| `LiveTrackingStreamControllerTest` | 5 | The WebSocket handler emits the cached view as a text message, sends 15 s ping heartbeats while waiting, honours the legacy `trackingId` query parameter, defaults to `unknown` when no tracking parameter is present, and completes after emitting a terminal status. |
| `TrackingBroadcastHubServiceTest` | 5 | Fee revisions reach the customer topic when `customerId` is present and fall back to the customer learned from the tracking number; they are ignored for unknown tracking numbers or duplicate event IDs; status events without a customer still update the cached view. |
| `StompTrackingWebSocketHandlerTest` | 7 (new) | CONNECT returns a connected frame, a public-topic subscribe streams the latest cached view, a private-topic subscribe without a token is rejected, a missing destination returns an error, an unsupported command returns an error, DISCONNECT closes the session and completes, and UNSUBSCRIBE sends a receipt. |

## RS256/JWKS evidence (already on `dev`)

| Test class | Cases | Proves |
|---|---|---|
| `JwtAuthenticationGlobalFilterTest` | 8 | HS256 happy path, missing/malformed Authorization header, forged identity header stripping, public-path sanitization, API-key bypass, expired token, wrong issuer, wrong audience. |
| `JwtAuthenticationGlobalFilterRs256Test` | 4 | RS256 token accepted through a JWKS lookup, unknown `kid` rejected, HS256 token rejected while RS256 is configured, token signed by an untrusted RSA key rejected. |
| `JwksKeyStoreTest` | 6 | Fetch and cache, throttled refetch for unknown `kid`, cooldown expiry, `cached()` before/after fetch, unsupported JWK entries skipped, malformed document rejected. |
| `TrackingTokenVerifierTest` | 5 | Missing/malformed token, HS256 principal extraction, wrong issuer/audience, public/private topic authorization. |
| `TrackingTokenVerifierRs256Test` | 4 | Cached RS256 key accepted, uncached key rejected, wrong issuer/audience rejected, HS256 token rejected while RS256 is configured. |

## Committed artifacts

- `surefire-reports/*.txt` - 18 suite summaries from the passing gateway run.
- `jacoco/index.html` - browsable JaCoCo report (open it locally).
- `jacoco/jacoco.csv` and `jacoco/jacoco.xml` - machine-readable per-class
  coverage used for the numbers above.

The XML surefire files and the `target/` build outputs are intentionally not
committed; they are reproducible with the commands above.

## Runtime smoke check (2026-11-12)

Run on `dev` at `7c2d826` with RabbitMQ and Redis from `docker compose`, the
gateway jar started with `--server.port=18080`. Port 8080 is inside a Windows
excluded-port range on this machine, so the check used 18080; the HTTP
behaviour is the same.

| Check | Observed result |
|---|---|
| `GET /actuator/health` | `{"status":"UP",...}` |
| Live `GET /v3/api-docs` | Lists `/api/v1/public/track/{trackingNumber}`, `/api/v1/tracking/{trackingNumber}`, and `/api/v1/tracking/{trackingNumber}/stream`. |
| RabbitMQ consumer | `swiftdrop.gateway.tracking.queue` exists; publishing a `ParcelStatusChangedEvent` for `SD-SMOKE-0001` (IN_TRANSIT) to `swiftdrop.events.exchange` with routing key `parcel.status.changed` returned `{"routed":true}`. |
| `GET /api/v1/public/track/SD-SMOKE-0001` | `SD-SMOKE-0001 IN_TRANSIT` |
| `GET /api/v1/tracking/SD-SMOKE-0001` | `SD-SMOKE-0001 IN_TRANSIT` (same payload as the public path) |
| `GET /api/v1/tracking/SD-SMOKE-0001/stream` | Emitted the cached view and a heartbeat within the 17 s probe window. |
| Rate limit | 60 requests returned 404 (cache miss with no order service running), then 5 returned `429` with `Retry-After: 27`. |

## Not yet verified

- **RS256 end-to-end with a live IAM instance**: not run. `dev` now defaults
  both the gateway and IAM to RS256 (`JWT_ALGORITHM=RS256` in `.env.example`
  and `docker-compose.yml`), but this run exercised unit and controller tests
  only, with no IAM instance running.
- **Full Docker Compose stack / AKS runtime**: RabbitMQ, Redis, and the gateway
  were exercised as described above, but the remaining services, the frontend
  container, and AKS were not; no live cluster was used.
- **Frontend live tracking demo**: not re-run here; this branch does not modify
  the frontend.

No tokens, passwords, private keys, or connection strings are stored in this
folder.
