# SwiftDrop AI Agent Entry Point

This file is the master operating contract for AI coding agents working in this monorepo. Read it before changing code, then read the nearest scoped `AGENTS.md` and any matching files under [`.agents/rules/`](.agents/rules/).

## System Overview and Architecture Map

SwiftDrop is a Java 25, Spring Boot 3.5.16 Maven monorepo with a reactive Spring Cloud Gateway, nine servlet business services, PostgreSQL database-per-service isolation, RabbitMQ event messaging, Flyway migrations, SpringDoc OpenAPI 3.1, and a React/TypeScript/Vite frontend.

The backend reactor modules are declared in [backend/pom.xml](backend/pom.xml): `common-contracts`, `api-gateway`, `auth-iam-service`, `customer-order-service`, `pricing-zone-service`, `dispatch-assignment-service`, `delivery-execution-service`, `merchant-b2b-service`, `webhook-resiliency-service`, `notification-service`, and `analytics-ai-service`.

### Ten application ownership boundaries

| Service | Member role | Local app port | Local Postgres container / host port | AKS Postgres headless DNS | Primary responsibility |
|---|---:|---:|---|---|---|
| `api-gateway` | Member 1 | 8080 | None; stateless | None | Routing, JWT/header propagation, CORS, rate limiting, and the Real-Time Tracking Hub |
| `auth-iam-service` | Member 2 | 8081 | `swiftdrop-iam-postgres` / 5431; `swiftdrop_iam` | `iam-postgres-headless.swiftdrop-data.svc.cluster.local` | Authentication, identity, and roles |
| `customer-order-service` | Member 3 | 8082 | `swiftdrop-order-postgres` / 5433; `swiftdrop_order` | `order-postgres-headless.swiftdrop-data.svc.cluster.local` | Customer order lifecycle |
| `pricing-zone-service` | Member 4 | 8083 | `swiftdrop-pricing-postgres` / 5434; `swiftdrop_pricing` | `pricing-postgres-headless.swiftdrop-data.svc.cluster.local` | Pricing quotes and geofence zones |
| `dispatch-assignment-service` | Member 5 | 8084 | `swiftdrop-dispatch-postgres` / 5435; `swiftdrop_dispatch` | `dispatch-postgres-headless.swiftdrop-data.svc.cluster.local` | Courier availability and assignment |
| `delivery-execution-service` | Member 6 | 8085 | `swiftdrop-delivery-postgres` / 5436; `swiftdrop_delivery` | `delivery-postgres-headless.swiftdrop-data.svc.cluster.local` | Delivery FSM, courier transitions, PoD, and delivery status events |
| `merchant-b2b-service` | Member 7 | 8086 | `swiftdrop-merchant-postgres` / 5437; `swiftdrop_merchant` | `merchant-postgres-headless.swiftdrop-data.svc.cluster.local` | Merchant APIs, API keys, and ingestion |
| `webhook-resiliency-service` | Member 8 | 8087 | `swiftdrop-webhook-postgres` / 5438; `swiftdrop_webhook` | `webhook-postgres-headless.swiftdrop-data.svc.cluster.local` | Webhook subscriptions, retry, and DLQ replay |
| `notification-service` | Member 9 | 8088 | `swiftdrop-notify-postgres` / 5439; `swiftdrop_notify` | `notify-postgres-headless.swiftdrop-data.svc.cluster.local` | Notification preferences and logs |
| `analytics-ai-service` | Member 10 | 8089 | `swiftdrop-analytics-postgres` / 5440; `swiftdrop_analytics` | `analytics-postgres-headless.swiftdrop-data.svc.cluster.local` | Operational KPIs and AI delivery assistance |

Kubernetes namespaces are `swiftdrop-apps`, `swiftdrop-data`, and `swiftdrop-messaging`, as defined by `namespaces` in the Helm chart [values.yaml](infra/helm/swiftdrop/values.yaml). The RabbitMQ headless service is `rabbitmq-headless` in `swiftdrop-messaging`. The Helm chart [infra/helm/swiftdrop](infra/helm/swiftdrop) is the only Kubernetes source; see [AKS-HELM-DEPLOYMENT.md](docs/deployment/AKS-HELM-DEPLOYMENT.md).

## Mandatory Pre-Coding Inspection Protocol

### Look before you code

- **REST endpoint or DTO:** inspect the owning controller, its service, [backend/common-contracts/src/main/java/com/swiftdrop/contracts/dto](backend/common-contracts/src/main/java/com/swiftdrop/contracts/dto), [backend/common-contracts/src/main/java/com/swiftdrop/contracts/enums](backend/common-contracts/src/main/java/com/swiftdrop/contracts/enums), and the matching baseline/live contract in [docs/api-specs](docs/api-specs). Check the gateway route and frontend generated types before changing a public path or payload.
- **RabbitMQ event flow:** inspect the event record under [backend/common-contracts/src/main/java/com/swiftdrop/contracts/events](backend/common-contracts/src/main/java/com/swiftdrop/contracts/events), every publisher/consumer, the owning `RabbitMQConfig.java`, and [infra/local/rabbitmq/definitions.json](infra/local/rabbitmq/definitions.json). Preserve correlation IDs and verify exchange, queue, binding, and routing-key parity.
- **Database entity:** inspect the owning service’s `src/main/resources/db/migration/` scripts and current repository/entity usage first. Database ownership is physical and strict: never add cross-service foreign keys, shared tables, or direct access to another service’s database.
- **UI view:** inspect the relevant persona feature folder and [frontend/src/design-system](frontend/src/design-system) first. Resolve density from [density.ts](frontend/src/design-system/tokens/density.ts): `COURIER` is `touch`, `CUSTOMER` is `comfortable`, and `ADMIN_OPS`/`MERCHANT` are `compact`. Reuse existing primitives and token mappings.

## Critical Architectural Boundaries

1. **Database per service:** the nine business services each own one PostgreSQL database and Flyway history. `api-gateway` is stateless and has no database.
2. **Member 1 gateway boundary:** [backend/api-gateway](backend/api-gateway) owns routing and the Real-Time Tracking Hub. It consumes `parcel.status.changed.#` from `swiftdrop.events.exchange`, sanitizes PII through `TrackingDataSanitizerProxy`, and publishes public updates through `/api/v1/tracking/{trackingId}/stream` and `/ws/tracking`.
3. **Member 6 delivery boundary:** [backend/delivery-execution-service](backend/delivery-execution-service) owns the delivery FSM, guards, delivery status publication, and Proof-of-Delivery persistence/verification. SwiftDrop does not use device location: couriers are matched by district. It must not own public tracking WebSocket/SSE handlers.
4. **API edge:** frontend REST traffic goes through `http://localhost:8080/api/v1/...`; downstream services are reached through gateway routes, not direct browser calls.
5. **Contracts:** shared event records, DTO wrappers, enums, security headers, and OpenAPI constants belong in `common-contracts`; do not duplicate them in a service.

## Mandatory Post-Coding Impact and Synchronization Matrix

| Change | Required follow-up |
|---|---|
| Add/modify REST controller or DTO | Verify `@Tag`, `@Operation`, `@Schema`, Jakarta validation, and `ApiResponse<T>` usage; update the relevant `docs/api-specs` contract; run `npm run api:sync:local` against a running gateway or `npm run api:sync:specs` from static specs. |
| Add/modify a JPA `@Entity` field | Add a new immutable Flyway migration named `V<N>__<description>.sql` in that service’s `src/main/resources/db/migration/`; never edit a committed migration. |
| Add/modify a RabbitMQ event record | Update [common-contracts](backend/common-contracts), rebuild it with `mvn -f backend/pom.xml clean install -pl common-contracts -am`, and update `definitions.json` plus the relevant `RabbitMQConfig.java` when queues or routing keys change. |
| Add an environment variable to `application.yml` | Update [.env.example](.env.example), [docker-compose.yml](docker-compose.yml), and the Helm chart: `services.<service>.config` in [values.yaml](infra/helm/swiftdrop/values.yaml) for non-secret values, or `services.<service>.secretEnv` plus `templates/secrets.yaml`, `values-secrets.example.yaml` and `scripts/generate-helm-secrets.sh` for secrets. A real secret value also goes into the `HELM_SECRETS_VALUES` environment secret on GitHub (see the runbook). |
| Change gateway route or public tracking stream | Update `GatewayRoutesConfig.java`, `JwtAuthenticationGlobalFilter.java`, gateway OpenAPI URLs, frontend tracking API/client, and verify WebSocket/SSE behavior. |
| Change a frontend contract type | Regenerate under `frontend/src/types/generated/`; do not hand-edit generated files. |

## Verified Build, Test, and Run Commands

Commands below match the repository’s current manifests and scripts:

```powershell
# Compile the complete backend reactor
mvn -f backend/pom.xml -DskipTests compile

# Focused gateway/delivery compilation
mvn -f backend/pom.xml -pl api-gateway,delivery-execution-service -am -DskipTests compile

# Rebuild shared contracts
mvn -f backend/pom.xml clean install -pl common-contracts -am

# Start local infrastructure and inspect health
docker compose up -d
docker compose ps
docker compose config --quiet

# Frontend validation
cd frontend
npm install
npm run lint
npm run build
npm run api:sync:specs
npm run api:sync:local
cd ..

# Helm chart (Kubernetes) — CI runs the same checks on pull requests
helm lint infra/helm/swiftdrop -f infra/helm/swiftdrop/values-secrets.example.yaml
helm template swiftdrop infra/helm/swiftdrop -f infra/helm/swiftdrop/values-student.yaml -f infra/helm/swiftdrop/values-secrets.example.yaml
```

Do not claim runtime success from compilation alone. For API/gateway changes, start the relevant services and verify health, OpenAPI, RabbitMQ consumption, and the affected HTTP/SSE/WebSocket path.

`mvn test` runs unit tests only. Integration tests (`*IT`, Testcontainers with real PostgreSQL and RabbitMQ) run in `mvn verify`, which is what CI runs. Run `mvn -f backend/pom.xml -pl <module> -am verify` before a PR that touches persistence, Flyway or messaging; Docker must be running. Each IT starts its own containers on random ports, so it never touches the Compose databases; the IAM ITs share one PostgreSQL, Redis and RabbitMQ through `IamContainers`.

## Working Rules

### Git and pull requests

- Never commit or push to `dev` or `main`. Branch from `dev` (`fix/…`, `feat/…`, `docs/…`, `refactor/…`), open a PR into `dev`, and merge it with **Squash and merge** (`dev` requires linear history).
- Releases are a `dev` → `main` PR merged with **Create a merge commit**, never squash. The procedure is in the [operations runbook](docs/deployment/OPERATIONS-RUNBOOK.md).
- Use Conventional Commit subjects (`feat(frontend): …`, `fix(api-docs): …`, `refactor!: …` for breaking changes) and explain the why in the body.
- If `dev` moved while you worked, merge `origin/dev` into your branch once and resolve conflicts there, not in the PR. Keep the other side's new work and re-apply your change on top. Then re-run the checks: regenerate API types, run lint, build and tests.
- If `main` has commits `dev` lacks (Dependabot security updates always target `main`), a `dev` → `main` release can conflict. Do not merge `main` into `dev`: `dev` squashes, which drops the link to `main` and the conflict returns. Branch `release/<date>` from `origin/dev`, merge `origin/main` there, resolve and run the checks, then PR it into `main` with a merge commit as the release. Add a small PR into `dev` that copies the resolved files, so both branches hold the same content. Check first with `git merge-tree --write-tree origin/main origin/dev`.
- PR descriptions list what changed, anything breaking, anything needed before deploying (migrations, Helm values, secrets), and how it was tested.

### Product decisions that constrain the code

- **Staff accounts are invited, never given a password.** Dispatchers register the couriers of their own district (`/api/v1/users/couriers`; the district comes from the dispatcher's account, never the request). Admins create dispatchers with `POST /api/v1/users` and no password. Both get an email link (`/invite#token=…`, 72 hours) where they choose a password. Couriers and dispatchers cannot change their own district.
- **Couriers use their own vehicle.** After the invite a courier registers it once (`PUT /api/v1/dispatch/couriers/me/profile`): motorcycle, three-wheeler, van or lorry (no bicycles), number plate, model, colour, licence. Only their dispatcher can change it afterwards. A shift cannot start without it and copies the vehicle type and capacity from it (defaults 5/10/25/50, dispatcher can override).
- **Merchants apply; the district dispatcher approves.** A business signs up at `/register/business` (MERCHANT role) with the form only: Google sign-up always creates a CUSTOMER account, so that page has no Google button, and no district either (the account district is optional for MERCHANT). It confirms its email, then submits its business details and one pickup location (`POST /api/v1/merchants/register`): name, what it sells, rough parcels a month, email, phone, pickup address, city, postal code and district. The registration number and the website or social media link are each optional, but at least one is required, because many small sellers are not registered and the dispatcher needs something to verify. Bulk CSV rows without a pickup city or postal code use the saved ones. The dispatcher of that pickup district approves or rejects it; a SYSTEM_ADMIN can decide for any district and suspends or reactivates approved merchants. A rejected business corrects the details and resubmits. Merchant endpoints check the caller's own merchant company (`MerchantAccess`); never trust a merchant id from the URL alone.
- **Cross-district parcels go hub to hub.** Same district: one courier, `PICKED_UP → IN_TRANSIT → OUT_FOR_DELIVERY`. Different districts: the pickup district's courier hands it in at their hub (`AT_ORIGIN_HUB`, the courier is freed), that district's dispatcher marks it Outbound (`IN_TRANSIT`), the drop-off district's dispatcher accepts it at their hub (`AT_DESTINATION_HUB`), assigns a courier of their district, who collects it (`OUT_FOR_DELIVERY`). Customers see the hub steps as `IN_TRANSIT`; the internal sub-status is `AT_HUB`/`OUTBOUND`/`INCOMING`. Dispatch tracks it with `leg` (`FIRST_MILE`/`LAST_MILE`), `hubStage` and the `IN_TRANSFER` status between the legs; the district managing an order is its `responsibleDistrict` (pickup on the first leg, drop-off on the last). `HubRoute` (delivery) enforces the route and who takes each hub step.
- **Dispatchers work inside their district.** An order belongs to its pickup district (its responsible district once the last leg starts): that district's dispatcher sees it, assigns and reassigns it, and only couriers working in that district can carry it (suggestions offer only them). The drop-off district's dispatcher sees it read-only (`GET /api/v1/dispatch/incoming`). Lists, courier views and workloads are limited to the dispatcher's district; the district comes from `X-District-Id` (their account), never the request. A SYSTEM_ADMIN works across districts. `DistrictScope` (dispatch), `DeliveryAccessPolicy` (delivery) and `CallerDistrict` (order service) enforce it.
- **Analytics follows the same districts.** A dispatcher gets only their district (`ReportDistrict`, from `X-District-Id`); a SYSTEM_ADMIN gets the whole island or `?district=`. A booking counts for its pickup district, an outcome for the district handling the parcel when it ended (the drop-off one once its hub accepted it, `OrderProjectionReducer.responsibleDistrict`), and a courier for the district that assigned their latest order.
- **AI is for customers only.** No other role has an assistant, predictor or AI log on screen.
- **Merchant integration is documented in the app** (`/merchant/api-guide`, samples in `features/merchant/apiGuide.ts`). Change it together with the B2B order contract, `WebhookPayloadV1`, `WebhookHeaders` or `WebhookSigner`; a test runs the signature code it shows. Failed webhooks are `webhook_deliveries` rows with status DEAD, replayed through `/api/v1/webhooks/deliveries/failed`; the old `/dlq` endpoints are legacy and no screen uses them.
- **A parcel goes only to a courier whose vehicle can carry it.** `CourierFit` (dispatch) is the one rule, used by the suggestions and by assign/reassign: on shift and free, longest side fits, and room left for its weight, volume and the parcel count. Limits per vehicle are in `VehicleType` (motorcycle 25 kg/100 l/50 cm, three-wheeler 150/600/100, van 600/4000/200, lorry 2000/15000/500). Dispatchers cannot override the size limits; a parcel nobody can take waits in the queue. Best match is the smallest vehicle that fits, then the emptiest courier. A courier's load is summed from the assignments they hold, never kept in a counter. Orders without a recorded size use the parcel count only. Bookings are capped at the lorry's limits (`ParcelLimits`), and merchants must send all three sides. Edits to an order reach dispatch through `order.updated`.
- **The dispatch console has one tab per job** (`/ops/dispatch`: To assign, Assigned, Ready to send, Incoming, Couriers). Do not stack the queues on one page again. Couriers are assigned only there; other pages link to it with `?tab=` and `?q=<tracking number>`.
- **Orders follow their pickup address, not the customer.** A customer has no district: sign-up does not ask and the booking form does not pre-fill one. Each order's pickup district decides which district handles it. Customers keep an address book (`/api/v1/orders/addresses`, up to 20, usable for pickup and recipient). An unfinished booking is kept in the browser per account for 7 days and removed on sign-out and after booking; each booking sends one `clientRequestId`, so a retried Confirm returns the same order instead of a second one.
- **The sender pays the delivery fee in cash at pickup.** There is no online payment. The courier collects the fee when they pick the parcel up, after any re-measure the customer has accepted. Every screen that shows a fee says so (`PAYMENT_NOTE` in the customer feature and the landing page). The cash-on-delivery ledger (`CashTransactionType`) is separate: it tracks money a merchant asks the courier to collect from the recipient, not the delivery fee.
- **One phone rule.** +94 and 9 digits, 0 and 9 digits, or just the 9 digits (never starting with 0); nothing else. The backend uses `SriLankanPhone` (common-contracts) and the frontend `src/utils/phone.ts`; do not write another pattern. Numbers are stored as +94 and 9 digits.
- **A courier sees only their own leg, and works only in their home district.** `GET /api/v1/deliveries/{id}/job` (`CourierJobService`) gives the assigned courier the sender and the fee until pickup, and the recipient only to the courier who delivers; nothing once the delivery is over or the order is cancelled. The addresses stay in the order service (read through `/internal/v1/orders/{id}/courier-job`, never stored or put on events). A shift always starts in the district of the courier's account.
- **No device location.** SwiftDrop matches couriers by district. Do not add GPS fields, endpoints, events, columns or `navigator.geolocation`; nginx sends `Permissions-Policy: geolocation=()`.
- **Branding.** The product name is "SwiftDrop" (the old "Velocity" name is retired everywhere). The logo is the `BrandMark` design-system component; never use a generic icon tile as the logo. The source image is `docs/brand/swiftdrop-logo.png`.
- **PWA.** The frontend is an installable PWA for couriers and customers. The service worker must never cache API responses, and updates wait for the user ("Reload"); see [frontend/AGENTS.md](frontend/AGENTS.md).
- **Swagger.** One UI at the gateway (`/swagger-ui.html`) lists every service. Each service's `OpenApiConfig` uses server `"/"` and JWT (`bearerAuth`) by default. `/internal/**` endpoints are excluded from the docs. Details are in [backend/AGENTS.md](backend/AGENTS.md).

### Environment pitfalls (Windows, Git Bash, PowerShell)

- Writing code or scripts through a shell heredoc can silently change backslashes and quotes (for example `\` line continuations become literal `\n`). Write multi-line content with a file-writing tool, or save a script to a file and run it, then re-read the result.
- Git on Windows runs with `core.autocrlf=true`, so "LF will be replaced by CRLF" warnings are normal. Keep each file's existing line endings when editing. Shell scripts must stay LF (`.gitattributes` enforces `*.sh eol=lf`) so they run in Linux containers and CI.
- In Git Bash, prefix `az`/`docker` commands that take `/`-paths with `MSYS_NO_PATHCONV=1`.

### Kubernetes context safety

- This machine can hold both the production context (`swiftdrop-aks`) and the local one (`docker-desktop`). Every `kubectl`/`helm` command against the local cluster must pass `--context docker-desktop` / `--kube-context docker-desktop`. Run the local build script with a pinned kubeconfig (`KUBECONFIG=/tmp/kube-local`), because it restarts deployments in the current context.
- Never run `helm uninstall`, `kubectl delete`, a restore or `az aks stop` without first checking `kubectl config current-context` and getting the owner's go-ahead. Production procedures are in the [operations runbook](docs/deployment/OPERATIONS-RUNBOOK.md).

## Repository Navigation

- Backend rules: [backend/AGENTS.md](backend/AGENTS.md)
- Frontend/design rules: [frontend/AGENTS.md](frontend/AGENTS.md)
- Backend contract rule: [.agents/rules/backend-contract-sync.md](.agents/rules/backend-contract-sync.md)
- Frontend ergonomics rule: [.agents/rules/frontend-ergonomics.md](.agents/rules/frontend-ergonomics.md)
- Infrastructure isolation rule: [.agents/rules/infra-db-isolation.md](.agents/rules/infra-db-isolation.md)
- Releases, pause/resume, backup/restore and incidents: [docs/deployment/OPERATIONS-RUNBOOK.md](docs/deployment/OPERATIONS-RUNBOOK.md)
- First-time AKS setup and full troubleshooting: [docs/deployment/AKS-HELM-DEPLOYMENT.md](docs/deployment/AKS-HELM-DEPLOYMENT.md)
- Local Kubernetes (Docker Desktop): [docs/deployment/LOCAL-KUBERNETES.md](docs/deployment/LOCAL-KUBERNETES.md)
- API contracts and Swagger: [docs/api-specs/README.md](docs/api-specs/README.md)
- RabbitMQ event payloads: [docs/design/m9/event-payload-schemas.md](docs/design/m9/event-payload-schemas.md)
- Who owns which service: [docs/member-guides/README.md](docs/member-guides/README.md)
