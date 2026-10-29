# SwiftDrop: per-member development guides (draft)

Written from the `dev` branch of [Shehan-Dinusha/BitCraft_SwiftDrop](https://github.com/Shehan-Dinusha/BitCraft_SwiftDrop/tree/dev) as of 27 Oct 2026 (commit `1a05498`). The assignment list (Member 1–10) decides **what** each member builds; the repo decides **how** (stack, folders, ports, names).

| # | Member | Guide | Backend module | Port | Postgres (host port) |
|---|---|---|---|---|---|
| 1 | Shehan | [member-1.md](member-1.md) | `api-gateway` | 8080 | none |
| 2 | Nimsara | [member-2.md](member-2.md) | `auth-iam-service` | 8081 | `iam-postgres` (5431*) |
| 3 | Dulanga | [member-3.md](member-3.md) | `customer-order-service` | 8082 | `order-postgres` (5433) |
| 4 | Sandali | [member-4.md](member-4.md) | `pricing-zone-service` | 8083 | `pricing-postgres` (5434) |
| 5 | Sehan | [member-5.md](member-5.md) | `dispatch-assignment-service` | 8084 | `dispatch-postgres` (5435) |
| 6 | Kasun | [member-6.md](member-6.md) | `delivery-execution-service` | 8085 | `delivery-postgres` (5436) |
| 7 | Uditha | [member-7.md](member-7.md) | `merchant-b2b-service` | 8086 | `merchant-postgres` (5437) |
| 8 | Hansana | [member-8.md](member-8.md) | `webhook-resiliency-service` | 8087 | `webhook-postgres` (5438) |
| 9 | Umesh | [member-9.md](member-9.md) | `notification-service` | 8088 | `notify-postgres` (5439) |
| 10 | Buddika | [member-10.md](member-10.md) | `analytics-ai-service` | 8089 | `analytics-postgres` (5440) |

\* compose says 5431, the service config and local guide say 5432. Member 1 and Member 2 align it.

**Stack (from the repo):** Java 25, Spring Boot 3.5.16, Spring Cloud Gateway 2025.0.0, Maven multi-module (`backend/pom.xml`), PostgreSQL 16 per service with Flyway, RabbitMQ 3.13 topic exchange `swiftdrop.events.exchange` + DLX, Redis 7, React 18 + TypeScript + Vite + Tailwind + React Query + Zustand (one app in `frontend/`), OpenAPI types generated with `npm run api:sync:specs`, AKS manifests in `infra/k8s/`.

## Important: most code already exists

The commit "Comprehensive implementation of SwiftDrop SRS across 8 microservice boundaries" scaffolded every service: controllers, entities, migrations, consumers, tests, and a frontend page per persona. So each guide is **"verify, close gaps, own it"**, not "build from zero". Each guide has a *Gaps to close* checklist comparing the repo with that member's assignment.

## Shared-file ownership (to stop members colliding)

| Shared file / folder | Owner | How others change it |
|---|---|---|
| `docker-compose.yml`, `.env.example`, root `README.md`, `docs/LOCAL-DEVELOPMENT.md` | Member 1 | Small PR adding only your service block / variables; Member 1 reviews |
| `backend/pom.xml` (parent), `.github/workflows/*` | Member 1 | Ask Member 1 |
| `api-gateway` (routes, filters, public paths) | Member 1 | Message Member 1 with the exact path to add |
| `frontend/src/app/*` (shell, `routes.tsx`, providers), `src/design-system/*` | Member 1 | Member 1 pre-creates every route + placeholder page in week 1; afterwards you only edit your own page files |
| `src/app/RoleGuard.tsx`, `AuthProvider`, `useAuthStore`, `apiClient.ts` | Member 2 | Ask Member 2 |
| `common-contracts/security/*`, enums `UserRole`, `PersonaRole`, `UserStatus` | Member 2 | Ask Member 2 |
| `common-contracts/events/**`, `EventRoutingKeys`, `infra/local/rabbitmq/definitions.json`, `infra/k8s/messaging/` | Member 9 | Request an additive change; Member 9 ships it and announces |
| Enums `DeliveryState`, `PublicOrderStatus`, `InternalQueueStatus`, `DeliveryFailureReason` | Member 6 | Ask Member 6 |
| Enums `OrderSpeed`, `ZoneTier`, `SriLankanDistrict` | Member 4 | Ask Member 4 |
| `common-contracts/dto/*` (`ApiResponse`, `ApiErrorResponse`, `PagedResult`) | Member 1 | Ask Member 1 |
| `frontend/package.json` | Member 1 | Add a dependency in its own tiny PR |
| `infra/k8s/services/<service>.yml`, `docs/api-specs/<service>`, `src/types/generated/<service>.ts` | That service's owner | Commit only your own generated file |

## Cross-member contracts (the only places members truly need each other)

| # | Provider → consumer | Contract | Fallback while waiting |
|---|---|---|---|
| C1 | M1 → all | Route skeleton, compose blocks, config-driven gateway URIs, JaCoCo, CI fix | Run your service on its own port |
| C2 | M2 → all | `CallerContext` header helper (`X-User-Id`, `X-User-Role`, `X-Merchant-Id`) | `@RequestHeader` |
| C3 | M2 ↔ M1 | RS256 + JWKS; gateway verifies with public key | HS256 behind `JWT_ALGORITHM` switch |
| C4 | M6 → M1, M3, M5, M8, M9, M10 | `OUT_FOR_DELIVERY` + public `FAILED` in enums | Treat unknown statuses neutrally |
| C5 | M4 → M3, M10 | `POST /api/v1/pricing/estimate` JSON (frozen in week 1) + `<PriceEstimatePreview>` | Member 3's local pricing copy |
| C6 | M3 → M7 | `POST /internal/v1/orders` (merchant orders go through order-service) | Member 7 keeps direct publish behind a flag |
| C7 | M3 → M1 | `GET /internal/v1/orders/tracking/{trackingNumber}` for gateway cache misses | Public orders endpoint |
| C8 | M9 → M5, M6 | `trackingNumber`, `merchantId` added to `CourierAssignedEvent` | Look up by `orderId` |
| C9 | M7 → M9 → M2 | `merchant.approved` event so the JWT gets `merchantId` | Admin sets `merchantId` manually |
| C10 | M2 → M9 | `GET /internal/v1/users/{id}/contact` | Fake contact |
| C11 | M9 → M8, M10 | `definitions.json` aligned; `order.created` bound for analytics | Java configs declare queues at startup |

Existing events that already work: `order.created`, `order.cancelled` (M3), `dispatch.courier.assigned`, `dispatch.failed` (M5), `parcel.status.changed` (M6, also M3/M5), `parcel.fee.revised`.

## Week-1 unblockers (do these first; everyone else can start on their own module immediately)

0. **Repo baseline (one person, `repo-fix-plan.md`):** credentials, IAM port, config-driven gateway routes, compose with all services + frontend, `OUT_FOR_DELIVERY`.
1. **M1:** route placeholders and shell slots (PR 1 of their guide).
2. **M2:** `CallerContext` helper; move `RoleGuard`.
3. **M6:** start the State Pattern refactor on top of the baseline enums.
4. **M4:** freeze the `/estimate` JSON.
5. **M9:** broker topology table, `definitions.json` parity, `CourierAssignedEvent` fields, `MerchantApprovedEvent`.

## Team rules (proposed; nothing in the repo defines them yet)

- Branch from `dev` as `feature/m<N>-<topic>`; PR back into `dev`; never commit to `main`.
- Commit style already used on `dev`: `feat(scope): …`, `fix(scope): …`.
- **Small commits:** every guide's Part B is split into stages. One stage = one commit with the suggested conventional message; a group of stages = one PR. No bulky "did everything" commits.
- At least one reviewer per PR; `dev` must always build.
- The baseline repo fixes (`repo-fix-plan.md`) are done separately by one person; every guide starts after them.
- After `common-contracts` changes merge, everyone runs `mvn -f backend\pom.xml -pl common-contracts -am -DskipTests install`.
- Diagrams go in `docs/design/m<N>/`, test evidence in `docs/test-reports/m<N>/`.

## Demo order (maps the assignment's demo list to members)

1. Customer login incl. Google (M2) → create delivery with estimate (M3, M4) → real-time tracking page (M1).
2. Merchant creates an order via API (M7) → receives a webhook (M8).
3. Dispatcher assigns a parcel (M5) → courier updates status (M6).
4. Notification fires (M9). Bonus: AI assistant + analytics (M10), Kubernetes (M1 + all).

## Conflicts and open decisions found while writing these guides

| # | Finding | Proposed answer in the guides | Needs Dula / team decision? |
|---|---|---|---|
| 1 | List says RS256; repo uses HS256 shared secret | Move to RS256 + JWKS behind a `JWT_ALGORITHM` switch (M2 + M1) | No (list wins) |
| 2 | List says `courier.assigned`; repo uses `dispatch.courier.assigned` | Keep repo name, already wired to M6 | Confirm |
| 3 | Merchant service publishes `order.created` itself | Route merchant orders through M3's `POST /internal/v1/orders` | **Yes** |
| 4 | Order service has its own copy of pricing | M3 calls M4's `/estimate`, deletes the copy | No |
| 5 | `DeliveryState` has no `OUT_FOR_DELIVERY`; public `FAILED` missing | M6 adds both in week 1 | No (list wins) |
| 6 | Delivery FSM is a lookup table, list says State Pattern | M6 refactors to state classes | No |
| 7 | Gateway routes use `lb://` with no service discovery | Config-driven URLs per service (M1) | No |
| 8 | Compose runs only gateway + auth; no frontend | M1 adds all services + frontend | No |
| 9 | CI filters on `services/**` (code is in `backend/**`), no JaCoCo | M1 fixes | No |
| 10 | DB password, RabbitMQ user and IAM port mismatches | Workarounds in each guide; owners fix their `application.yml` | No |
| 11 | No branch naming or PR rules in the repo | `feature/m<N>-<topic>` → `dev` | Confirm |
| 12 | No owner in the list for "Manage users" screen, Kubernetes bonus, shell/route table | M2 gets user management; M1 gets K8s base + shell | Confirm |
| 13 | Tracking number format `SD-XXXX-XXXX` vs list example `SWIFT-8923-LK` | Keep repo format (list says "e.g.") | Confirm |
| 14 | Frontend roles `ADMIN_OPS` vs backend `DISPATCHER` + `SYSTEM_ADMIN` | M2 decides one mapping | No |
| 15 | No deadline in the repo or list | Guides use phases, not dates | **Yes**, if you want dates |
| 16 | M3 handoff pack (port 8083, `lk.swiftdrop`, table `parcels`) predates the repo | Repo wins; pack still useful for rules/mock | No |
