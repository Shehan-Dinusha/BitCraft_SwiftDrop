# Member 3: Dulanga · Customer Order & Receipt Service

> Draft guide, written from the `dev` branch of [Shehan-Dinusha/BitCraft_SwiftDrop](https://github.com/Shehan-Dinusha/BitCraft_SwiftDrop/tree/dev) as of 27 Oct 2026 (commit `1a05498`).
> Your assignment list is the source of truth for **what** you build. The repo is the source of truth for **how** (stack, folders, ports, names).

## 0. Your scope at a glance

| Item | Value |
|---|---|
| Backend module | `backend/customer-order-service` |
| Local app port | `8082` |
| Database | `order-postgres` container, host port `5433`, database `swiftdrop_order` |
| Frontend folders you own | `frontend/src/features/customer/` booking + history files (`OrderBookingWizard.tsx`, `CustomerOrderHistory.tsx`, `pages/CustomerBookingPage.tsx`, new `pages/OrderHistoryPage.tsx`), `src/services/api/orderApi.ts` |
| Documentation deliverable | Use case diagram for Customer interactions + sequence diagram for Delivery Request Creation |
| Branch prefix | `feature/m3-<short-topic>` (off `dev`, PR back into `dev`) |

Read [README.md](README.md) in this folder once. It has the shared-file ownership map and the list of cross-member contracts.

## 1. What already exists on `dev`

The service is already heavily scaffolded. Your job is to verify it against your assignment, close gaps, and own it.

- `OrderController` at `/api/v1/orders`: `POST /estimate`, `POST /`, `GET /{orderId}`, `GET /tracking/{trackingNumber}`, `GET /` (list), `PUT`, `DELETE`, `POST /{id}/cancel`, fee-revision accept/reject, `GET /{id}/shipping-label` and `GET /{id}/delivery-receipt` (PDF).
- `TrackingNumberGenerator`: `SecureRandom`, format `SD-XXXX-XXXX` (e.g. `SD-7K4M-9QX2`).
- `OrderPdfGeneratorService` with OpenPDF + ZXing (QR code).
- `OrderEventPublisher`: publishes `order.created`, `order.cancelled` and some `parcel.status.changed` events.
- `PricingCalculationService`: a **local copy** of pricing logic.
- Flyway: `V1__init_order_schema.sql`, `V2__expand_customer_order_schema.sql` (tables `customer_orders`, `order_address`, `order_item`).
- Tests: controller, service, PDF, pricing, tracking-number.
- Frontend: `OrderBookingWizard`, `CustomerOrderHistory`, `CustomerBookingPage`, `orderApi.ts`.

> **Your handoff pack** (`/handoff/` in the project files) predates the repo. Where they differ, the repo wins: port **8082** (not 8083), package `com.swiftdrop.order` (not `lk.swiftdrop.order`), table `customer_orders` (not `parcels`). The pack is still useful for validation rules, the UI mock and diagrams. Update `handoff/CLAUDE.md` before using it in a Claude Code session.

## 2. Gaps to close

- [ ] **Duplicated pricing logic.** `PricingCalculationService` copies Member 4's engine (same `PRICING-2026-V1` constant). Replace it with a `PricingClient` that calls Member 4's `/api/v1/pricing/estimate`; keep the local class only as a fallback behind the interface until Member 4's endpoint is live, then delete it.
- [ ] **Merchant orders bypass you.** Member 7's service currently publishes `order.created` itself, so merchant parcels never exist in your database (no history, no receipt, no tracking lookup). Add `POST /internal/v1/orders` for Member 7 (see Dependencies) and make your service the **only** publisher of `order.created`.
- [ ] **Ownership checks.** `GET /api/v1/orders/{id}`, list, cancel and the PDF endpoints must only return orders where `customerId` = caller (`X-User-Id`), unless the role is `DISPATCHER`/`SYSTEM_ADMIN`.
- [ ] **Receipt on confirmation.** Assignment says the receipt is generated "upon confirmation". Decide: generate on demand (current) or store at creation. Either is fine; document it.
- [ ] **Input sanitisation:** Jakarta validation on every address field (length, allowed characters), reject HTML/script, parameterised queries only.
- [ ] **Internal lookup for the gateway:** `GET /internal/v1/orders/tracking/{trackingNumber}` returning public-safe fields + status history for Member 1.
- [ ] **Order history dashboard** page with one-click "Download PDF Receipt".
- [ ] **Multi-step form validation** (pickup → drop-off → parcel size/weight → review with price → confirm).
- [ ] Add a `customer-order-service` block in compose (Member 1 adds the skeleton; you verify env vars).

## 3. Boundaries

**You own:** `backend/customer-order-service/`; `common-contracts/events/order/*` field **values** (Member 9 owns the record files, you request changes); frontend booking and history files listed above; `docs/api-specs` for orders.

**You do not touch:** pricing rules (Member 4); `PriceEstimatePreview` component internals (Member 4 builds it, you place it in step 4 of the wizard); dispatch, delivery or tracking-hub code.

## 4. Who you depend on

| Member | What you need | Until it arrives |
|---|---|---|
| Member 4 (Sandali) | `POST /api/v1/pricing/estimate` request/response contract and the `<PriceEstimatePreview>` component props | Keep local `PricingCalculationService` behind `PricingClient`; show a plain price line in the wizard |
| Member 7 (Uditha) | Agreement on `POST /internal/v1/orders` body (normalised merchant order + `merchantId`, `externalRef`) | Build and test the endpoint yourself with sample payloads |
| Member 2 (Nimsara) | `CallerContext` helper | Read `X-User-Id`/`X-User-Role` with `@RequestHeader` |
| Member 9 (Umesh) | Final `OrderCreatedEvent` schema | Use current record |

Others depend on you for: `order.created` (Members 5, 8, 9, 10), the internal tracking lookup (Member 1), the internal create endpoint (Member 7).

## Part A. One-time setup (about 1 hour)

**A1. Install tools.** Java 25 (Temurin), Maven 3.9+, Node.js 20+, Docker Desktop, Git, VS Code with *Extension Pack for Java* and *Spring Boot Extension Pack*. Check them:

```powershell
java -version; mvn -version; node --version; npm --version; docker --version; docker compose version
```

**A2. Clone and branch from `dev`** (never work on `main`):

```powershell
git clone https://github.com/Shehan-Dinusha/BitCraft_SwiftDrop.git
cd BitCraft_SwiftDrop
git checkout dev
git pull origin dev
git checkout -b feature/m3-setup
```

**A3. Environment file.**

```powershell
Copy-Item .env.example .env
```

Never commit `.env`. Every new variable you add goes into `.env.example`, `docker-compose.yml` and `infra/k8s/services/customer-order-service.yml` (rule from `AGENTS.md`).

> **Known setup traps (skip this box once `repo-fix-plan.md` is merged on `dev`):**
> 1. `docker-compose.yml` starts Postgres with password `changeme_pg`, but services default to `swiftdrop`.
> 2. Compose creates the RabbitMQ user `swiftdrop`/`changeme_rabbit`, but most services set no RabbitMQ user, so Spring falls back to `guest`/`guest` and cannot connect.
>
> In each terminal where you run your service, set these first:
>
> ```powershell
> $env:DB_PASSWORD = 'changeme_pg'
> $env:SPRING_RABBITMQ_USERNAME = 'swiftdrop'
> $env:SPRING_RABBITMQ_PASSWORD = 'changeme_rabbit'
> ```
>
> Then, in your first PR, add `username: ${RABBITMQ_USERNAME:swiftdrop}` and `password: ${RABBITMQ_PASSWORD:changeme_rabbit}` under `spring.rabbitmq` in **your** `application.yml`.

**A4. Install the shared contracts jar** (repeat whenever `backend/common-contracts` changes on `dev`):

```powershell
mvn -f backend\pom.xml -pl common-contracts -am -DskipTests install
```

**A5. Install frontend packages.**

```powershell
cd frontend; npm install; cd ..
```

**A6. Start only the infrastructure you need.**

```powershell
docker compose up -d rabbitmq order-postgres
docker compose ps
```

RabbitMQ UI: http://localhost:15672 (user `swiftdrop`, password `changeme_rabbit`).

**A7. Run your service and confirm it boots.**

```powershell
mvn -f backend\pom.xml -pl customer-order-service -am spring-boot:run
```

Open http://localhost:8082/actuator/health and http://localhost:8082/swagger-ui.html.

**A8. Record today's baseline.** Run your module's tests once and write down what passes and fails before you change anything:

```powershell
mvn -f backend\pom.xml -pl customer-order-service -am test
```

**A9. Read before coding:** `AGENTS.md`, `backend/AGENTS.md`, `frontend/AGENTS.md`, `docs/LOCAL-DEVELOPMENT.md`, `frontend/COMPONENTS.md`, and `.agents/rules/*.md`. They define package layout, DTO rules (Java records + `@Schema`), Flyway rules (new `V<N>__*.sql`, never edit an old one), and the Velocity design-system rules.

## Part B0. How you work every day (same for all members)

1. Start from fresh `dev`: `git checkout dev; git pull origin dev; git checkout -b feature/m3-<topic>`.
2. Work stage by stage (Part B): **one stage = one commit**, one group of stages = one PR. Small commits are easy to review and to revert.
3. Commit messages follow the style already on `dev`: `feat(<scope>): ...`, `fix(<scope>): ...`, `test(<scope>): ...`, `docs(<scope>): ...`.
4. Only edit files you own (section "Boundaries"). If you need a change in someone else's file, message that member with the exact change, or open a tiny PR and ask them to review it.
5. Before pushing: compile and test your module, and if you touched the frontend run `npm run lint` and `npm run build` in `frontend/`.
6. Open the PR into `dev`, ask at least one teammate to review, and merge only when it builds.
7. After `common-contracts` changes land on `dev`, everyone re-runs step A4.

## Part B. Stage-by-stage implementation plan

Each **stage** below is one small, reviewable commit. Finish a stage, check its *Done when* line, then commit with the suggested message. Stages are grouped into **PRs**: push your branch after every stage, and open the PR when the group is done.

Rules:
- One stage = one commit. Don't mix unrelated changes (e.g. a refactor and a new endpoint) in one commit.
- Commit only when the code compiles and the stage's check passes. Never commit broken code to save work; use `git stash` instead.
- If a stage grows past ~300 changed lines, split it and commit the first half.
- Tests live in the same stage as the code they test, unless the stage is explicitly a test stage.
- Branch per PR: `feature/m3-<pr-topic>` from fresh `dev`.

> **Before you start:** `repo-fix-plan.md` (credentials, gateway URLs, compose) is being done separately. Pull `dev` once it's merged. Your handoff pack's port/package/table names are superseded by the repo (see section 1).


### PR 1: Verify and secure what exists


#### Stage 1: Smoke-check the existing flow

**Objective:** Know what works before changing anything.

**Steps:**

1. Create an order in Swagger (`http://localhost:8082/swagger-ui.html`), check the row in `swiftdrop_order` and the `order.created` message in the RabbitMQ UI.
2. Write findings in `docs/design/m3/baseline-notes.md`.

**Commit:** `docs(order): record baseline behaviour of order service`

**Done when:** Notes committed; you know which endpoints work.


#### Stage 2: Ownership on reads

**Objective:** Customers only see their own orders.

**Steps:**

1. In `CustomerOrderService` get/list: if role is CUSTOMER, filter by `customerId = X-User-Id` (use `CallerContext` when Member 2 ships it, `@RequestHeader` until then).
2. `OrderOwnershipTest`: customer A → 404 on customer B's order.

**Commit:** `feat(order): restrict order reads to the owning customer`

**Done when:** Test passes; dispatcher can still read any order.


#### Stage 3: Ownership on cancel and PDFs

**Objective:** Same rule for writes and downloads.

**Steps:**

1. Apply the same check to cancel, shipping-label and delivery-receipt endpoints; extend `OrderOwnershipTest`.

**Commit:** `feat(order): restrict cancel and PDF downloads to the owner`

**Done when:** Customer B gets 404 on customer A's receipt.


#### Stage 4: Address validation

**Objective:** Reject unsafe or malformed input.

**Steps:**

1. On `CreateOrderRequest` and nested address DTOs: `@NotBlank`, `@Size(5,200)`, `@Pattern` banning `<` and `>`; phone `^0\d{9}$`; weight `@DecimalMin(0.01) @DecimalMax(50)`; dimensions positive.
2. `CreateOrderValidationTest`: SQLi (`' OR 1=1 --`) and `<script>` → 400.

**Commit:** `feat(order): validate and sanitise order inputs`

**Done when:** Test passes; valid orders still succeed.


#### Stage 5: Safe error bodies

**Objective:** No personal data or SQL in errors.

**Steps:**

1. Review `GlobalExceptionHandler`: return field names and generic messages only, never rejected values; log without addresses/phones.
2. Assert in `CreateOrderValidationTest` that the error body doesn't echo the input.

**Commit:** `fix(order): remove user input from error responses`

**Done when:** Error JSON for a bad address contains no address text.


### PR 2: Pricing through Member 4


#### Stage 6: PricingClient interface

**Objective:** Prepare to stop duplicating pricing.

**Steps:**

1. Create `PricingClient` interface; wrap the existing `PricingCalculationService` as `LocalPricingFallback implements PricingClient`.
2. Switch `/estimate` and order creation to use `PricingClient`. No behaviour change.

**Commit:** `refactor(order): introduce PricingClient abstraction`

**Done when:** Existing tests pass unchanged.


#### Stage 7: HTTP pricing client

**Objective:** Call the real pricing service.

**Steps:**

1. `HttpPricingClient` calling `${PRICING_SERVICE_URL}/api/v1/pricing/estimate` (contract from Member 4), 2 s timeout.
2. Choose the bean by `PRICING_MODE=remote|local` (default local until Member 4 is live).
3. MockWebServer test.

**Commit:** `feat(order): call pricing service for estimates`

**Done when:** With `PRICING_MODE=remote` and pricing running, `/estimate` returns Member 4's breakdown.


#### Stage 8: Remove local pricing copy

**Objective:** One source of pricing truth.

**Steps:**

1. When Member 4's endpoint is on `dev`: default `PRICING_MODE=remote`, delete `PricingCalculationService` and its test, keep a simple error when pricing is down.

**Commit:** `refactor(order): remove duplicated pricing logic`

**Done when:** Build passes; estimate still works end to end.


### PR 3: Integration endpoints


#### Stage 9: Internal tracking lookup

**Objective:** Gateway can show tracking after a restart.

**Steps:**

1. `GET /internal/v1/orders/tracking/{trackingNumber}` → status, status history, pickup/drop-off city and district only.
2. Tell Member 1 it's ready.

**Commit:** `feat(order): add internal tracking lookup for gateway`

**Done when:** Calling it on 8082 returns the sanitised view; through the gateway it's blocked.


#### Stage 10: Internal merchant order create

**Objective:** Merchant orders go through the order service.

**Steps:**

1. `POST /internal/v1/orders` with `merchantId`, `externalRef`, pickup, drop-off, parcel; set `source = MERCHANT` (new Flyway `V3__order_source.sql` adding `source`, `external_ref`, unique `(merchant_id, external_ref)`).
2. Publishes the same `order.created`.

**Commit:** `feat(order): accept merchant orders via internal endpoint`

**Done when:** Posting the same `externalRef` twice returns the same order.


#### Stage 11: Event integration test

**Objective:** Required test for the event flow.

**Steps:**

1. `OrderPersistenceAndEventIT` (Testcontainers Postgres + RabbitMQ): `POST /orders` persists the row and an `order.created` message with the same tracking number reaches a test queue bound to the exchange.

**Commit:** `test(order): verify order persistence and order.created event`

**Done when:** IT passes locally with Docker running.


#### Stage 12: Status sync from deliveries

**Objective:** Order status follows real delivery progress.

**Steps:**

1. Bind `parcel.status.changed.#` in your `RabbitMQConfig` to a new queue `customer-order.status-sync.queue`; update order status on events (idempotent on `eventId`).
2. Send Member 9 the queue/binding for `definitions.json`.

**Commit:** `feat(order): sync order status from parcel status events`

**Done when:** Moving a delivery to IN_TRANSIT changes the order's status in your DB.


### PR 4: Receipts


#### Stage 13: Receipt content

**Objective:** Receipt matches the assignment.

**Steps:**

1. Check A5 receipt: tracking number, QR to `/track/<number>`, fee, pickup/drop-off city, timestamps, no phone numbers.
2. Extend `OrderPdfGeneratorServiceTest` (non-empty, contains tracking number, no phone).

**Commit:** `feat(order): finalise delivery receipt layout`

**Done when:** Downloaded PDF opens and shows the expected fields.


#### Stage 14: Download headers

**Objective:** One-click download in browsers.

**Steps:**

1. Set `Content-Disposition: attachment; filename=receipt-<tracking>.pdf` and `Cache-Control: no-store`.

**Commit:** `fix(order): set receipt download headers`

**Done when:** Browser downloads the file with the right name.


### PR 5: Booking form


#### Stage 15: Wizard steps skeleton

**Objective:** Multi-step structure.

**Steps:**

1. Split `OrderBookingWizard` into Pickup, Drop-off, Parcel, Review steps with Next/Back; state kept in one form object.

**Commit:** `feat(frontend): split booking wizard into four steps`

**Done when:** You can move between steps without losing data.


#### Stage 16: Per-step validation

**Objective:** Users can't continue with bad data.

**Steps:**

1. Validate each step before Next (same rules as the backend); inline error messages.

**Commit:** `feat(frontend): validate each booking step`

**Done when:** Next is blocked on an invalid phone or weight.


#### Stage 17: Price preview

**Objective:** Show the estimate before confirming.

**Steps:**

1. On Review, render Member 4's `<PriceEstimatePreview request={...}/>`; until it exists, call `/api/v1/orders/estimate` and show the total.

**Commit:** `feat(frontend): show price estimate on booking review`

**Done when:** Changing weight updates the price.


#### Stage 18: Confirm and result

**Objective:** Create the order and show the tracking number.

**Steps:**

1. On confirm, `POST /api/v1/orders`; show tracking number with a link to `/track/<number>` and a receipt download button.

**Commit:** `feat(frontend): submit booking and show tracking number`

**Done when:** New order visible in history and tracking page.


### PR 6: Order history


#### Stage 19: History table

**Objective:** Customers see past deliveries.

**Steps:**

1. `OrderHistoryPage` at `/customer/orders`: table with tracking number, date, destination city, `FsmStatusBadge`, fee.

**Commit:** `feat(frontend): add customer order history page`

**Done when:** Orders list loads for the logged-in customer.


#### Stage 20: Filters and receipt download

**Objective:** One-click receipt.

**Steps:**

1. Status filter; 'Download PDF Receipt' button fetching the PDF as a blob and saving it.

**Commit:** `feat(frontend): add receipt download to order history`

**Done when:** Clicking the button downloads the PDF.


### PR 7: Documentation


#### Stage 21: Diagrams

**Objective:** Your assigned deliverable.

**Steps:**

1. Update the customer use case and delivery-creation sequence diagrams to repo names (port 8082, `customer_orders`, pricing call); save to `docs/design/m3/`.

**Commit:** `docs(order): add customer use case and order creation sequence`

**Done when:** Files committed and shared.

## Part C. Testing and security (summary)

Every test below is written and committed inside a stage in Part B. Use this table as a final checklist.

| Test | Type | What it proves |
|---|---|---|
| `CreateOrderValidationTest` | Unit (MockMvc) | SQLi strings (`' OR 1=1 --`) and XSS (`<script>`) in address fields → 400; error body has no echoed input |
| `OrderPersistenceAndEventIT` | Integration (Testcontainers Postgres + RabbitMQ) | `POST /orders` persists the row **and** an `order.created` message arrives on a test queue with the same tracking number |
| `OrderOwnershipTest` | Unit | Customer A gets 403/404 on Customer B's order and receipt |
| `TrackingNumberGeneratorTest` (extend) | Unit | 10,000 numbers, no duplicates, format `SD-[A-Z0-9]{4}-[A-Z0-9]{4}` |
| `InternalMerchantOrderTest` | Unit | Same `merchantId + externalRef` twice creates one order |
| `OrderPdfGeneratorServiceTest` (extend) | Unit | PDF is non-empty, contains tracking number, no phone number |

This covers the assignment's "at least one test for the event/integration flow".

## Part D. Documentation deliverable and demo

1. *Customer use case diagram* (you already have `m3-use-case.puml`/PNG in project files; update names to match the repo).
2. *Sequence diagram: Delivery Request Creation* (`m3-sequence.puml`): customer → frontend → gateway → order-service → pricing-service (estimate) → DB → RabbitMQ `order.created` → dispatch/notification consumers.

**Your demo part:** create a delivery in the wizard, show the estimate, confirm, show the tracking number, download the PDF receipt, and show `order.created` in the RabbitMQ UI.

## Part E. Finishing up

**E1. Definition of done (tick every box before you call your part finished).**

- [ ] Every item in your "Gaps to close" checklist is done or has a written reason why not.
- [ ] `mvn -f backend\pom.xml -pl customer-order-service -am verify` passes locally.
- [ ] Coverage report exists for your module (once Member 1 adds JaCoCo: `backend/customer-order-service/target/site/jacoco/index.html`). Aim for **70%+ line coverage** on `service/` and `controller/`.
- [ ] Swagger docs for your endpoints are complete (`@Tag`, `@Operation`, `@Schema`) and `docs/api-specs` is updated.
- [ ] Frontend types regenerated with `npm run api:sync:specs` (commit only **your** service's generated file).
- [ ] `npm run lint` and `npm run build` pass in `frontend/`.
- [ ] DB and RabbitMQ credentials in `application.yml` come from env variables (no hard-coded values).
- [ ] No secrets in code, no personal data (phone, address, names) in logs or error messages.
- [ ] Your service block in `docker-compose.yml` starts and is healthy (`docker compose up -d customer-order-service`).
- [ ] `infra/k8s/services/customer-order-service.yml` has every env variable your `application.yml` reads (bonus marks).
- [ ] Documentation deliverable exported (PNG/PDF + source) to `docs/design/m3/` and sent to whoever compiles the final design PDF.

**E2. Test evidence for the submission.** Save `backend/customer-order-service/target/surefire-reports/` and the JaCoCo `index.html` (as a screenshot or PDF) into `docs/test-reports/m3/`.

**E3. Demo preparation.** Rehearse your part of the demo script (Part D) twice on a clean `docker compose down -v` start. Be ready to explain your code, your diagrams, and one security decision you made.

**E4. Contribution sheet line.** Send the team lead a 3-line summary: service, frontend screens, tests and diagrams you delivered, with links to your merged PRs.
