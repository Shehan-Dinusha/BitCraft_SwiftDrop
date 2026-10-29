# Member 6: Kasun · Delivery Execution & Proof-of-Delivery

> Draft guide, written from the `dev` branch of [Shehan-Dinusha/BitCraft_SwiftDrop](https://github.com/Shehan-Dinusha/BitCraft_SwiftDrop/tree/dev) as of 27 Oct 2026 (commit `1a05498`).
> Your assignment list is the source of truth for **what** you build. The repo is the source of truth for **how** (stack, folders, ports, names).

## 0. Your scope at a glance

| Item | Value |
|---|---|
| Backend module | `backend/delivery-execution-service` |
| Local app port | `8085` |
| Database | `delivery-postgres` container, host port `5436`, database `swiftdrop_delivery` |
| Frontend folders you own | `frontend/src/features/courier/` (all), `src/services/api/deliveryApi.ts`, `src/hooks/useFsmTransition.ts`, `src/store/useActiveDeliveryStore.ts` |
| Documentation deliverable | Sequence diagram for courier status updates + State Machine diagram of the delivery lifecycle |
| Branch prefix | `feature/m6-<short-topic>` (off `dev`, PR back into `dev`) |

Read [README.md](README.md) in this folder once. It has the shared-file ownership map and the list of cross-member contracts.

## 1. What already exists on `dev`

- `CourierAssignedConsumer` creates a `DeliverySession` from `dispatch.courier.assigned`.
- `DeliveryExecutionController` `/api/v1/deliveries`: get by id/order/tracking, `GET /courier/{courierId}`, `POST /{id}/transition`, `POST /{id}/location`, breadcrumbs. `ProofOfDeliveryController`: upload, verify, get.
- `DeliveryStateMachine`: a transition **table** (`EnumMap`) `CREATED → ASSIGNED → PICKED_UP → IN_TRANSIT → DELIVERED | FAILED`, plus guards (`GeofenceProximityGuard`, `ProofOfDeliveryGuard`) and actions (audit, publish).
- `DeliveryEventPublisher`: `DeliveryStateChangedEvent`, `LocationUpdatedEvent`, `parcel.status.changed`.
- Flyway V1–V3; five test classes.
- Frontend: `CourierShiftDashboard`, `ActiveDeliveryExecutionPage`, `OneTapStateTransitionCard`, `ProofOfDeliveryCaptureModal`, `GpsTelemetryPublisher`.

## 2. Gaps to close

- [ ] **`OUT_FOR_DELIVERY` is missing** from `DeliveryState`, and `PublicOrderStatus.fromDeliveryState` maps `FAILED → RETURNED_TO_SENDER` (there is no public `FAILED`). Your list requires Created → Picked Up → In Transit → Out for Delivery → Delivered | Failed. This enum fix is step 4 of `repo-fix-plan.md`, done separately; you own the enums after that.
- [ ] **State Pattern.** Your list says State Pattern, the repo has a lookup table. Refactor to one class per state (`CreatedState`, `PickedUpState`, …) implementing `DeliveryStateHandler` with `next(...)`/`fail(...)`; keep the table only as a test oracle if you like.
- [ ] **Horizontal authorisation is missing.** `GET /courier/{courierId}` and `POST /{id}/transition` don't check the caller. Courier A must not view or update Courier B's parcels.
- [ ] **Multipart proof upload:** confirm `POST /{id}/proof-of-delivery` accepts `multipart/form-data` (photo ≤ 5 MB, jpeg/png), recipient name, optional signature; store the file on a volume path from env (`POD_STORAGE_DIR`) and only a reference in DB.
- [ ] **Failure reasons:** use `DeliveryFailureReason` enum codes; reason is required for FAILED.
- [ ] **Tracking number** on outgoing `parcel.status.changed`: needs Member 5 to send it on `CourierAssignedEvent` (via Member 9's contract change).
- [ ] "Today's deliveries" list endpoint for the logged-in courier: `GET /api/v1/deliveries/me?date=today`.

## 3. Boundaries

**You own:** `backend/delivery-execution-service/`; enums `DeliveryState`, `PublicOrderStatus`, `InternalQueueStatus`, `DeliveryFailureReason` in `common-contracts`; all of `frontend/src/features/courier/`.

**You do not touch:** public tracking SSE/WebSocket (Member 1, rule in `AGENTS.md`); dispatch assignment; event record files (ask Member 9).

## 4. Who you depend on

| Member | What you need | Until it arrives |
|---|---|---|
| Member 5 (Sehan) | `dispatch.courier.assigned` with `trackingNumber`; shift start/end API for the courier dashboard | Create sessions from a test message in the RabbitMQ UI |
| Member 9 (Umesh) | Adds `trackingNumber`/`merchantId` to `CourierAssignedEvent` | Look up by `orderId` only |
| Member 2 (Nimsara) | `CallerContext` (`X-User-Id`, role) | `@RequestHeader` |

Others depend on you for: `parcel.status.changed` (Members 1, 3, 5, 8, 9, 10).

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
git checkout -b feature/m6-setup
```

**A3. Environment file.**

```powershell
Copy-Item .env.example .env
```

Never commit `.env`. Every new variable you add goes into `.env.example`, `docker-compose.yml` and `infra/k8s/services/delivery-execution-service.yml` (rule from `AGENTS.md`).

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
docker compose up -d rabbitmq delivery-postgres
docker compose ps
```

RabbitMQ UI: http://localhost:15672 (user `swiftdrop`, password `changeme_rabbit`).

**A7. Run your service and confirm it boots.**

```powershell
mvn -f backend\pom.xml -pl delivery-execution-service -am spring-boot:run
```

Open http://localhost:8085/actuator/health and http://localhost:8085/swagger-ui.html.

**A8. Record today's baseline.** Run your module's tests once and write down what passes and fails before you change anything:

```powershell
mvn -f backend\pom.xml -pl delivery-execution-service -am test
```

**A9. Read before coding:** `AGENTS.md`, `backend/AGENTS.md`, `frontend/AGENTS.md`, `docs/LOCAL-DEVELOPMENT.md`, `frontend/COMPONENTS.md`, and `.agents/rules/*.md`. They define package layout, DTO rules (Java records + `@Schema`), Flyway rules (new `V<N>__*.sql`, never edit an old one), and the Velocity design-system rules.

## Part B0. How you work every day (same for all members)

1. Start from fresh `dev`: `git checkout dev; git pull origin dev; git checkout -b feature/m6-<topic>`.
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
- Branch per PR: `feature/m6-<pr-topic>` from fresh `dev`.

> **Before you start:** the enum change (`OUT_FOR_DELIVERY` and public `FAILED`) is step 4 of `repo-fix-plan.md`, done separately by one person. Pull `dev` once it's merged; your stages build on it.


### PR 1: State Pattern


#### Stage 1: State handler interface

**Objective:** Define the pattern.

**Steps:**

1. Add `DeliveryStateHandler` interface: `DeliveryState state()`, `Set<DeliveryState> allowedNext()`, `void validate(TransitionDeliveryRequest, DeliverySession)`.

**Commit:** `refactor(delivery): introduce delivery state handler interface`

**Done when:** Compiles; no behaviour change.


#### Stage 2: One class per state

**Objective:** Each state owns its rules.

**Steps:**

1. Add `CreatedState`, `AssignedState`, `PickedUpState`, `InTransitState`, `OutForDeliveryState`, `DeliveredState`, `FailedState`, `CancelledState` as `@Component`s.
2. `StateHandlersTest`: each allows only its next states.

**Commit:** `feat(delivery): add state classes for delivery lifecycle`

**Done when:** Tests pass.


#### Stage 3: Machine delegates to states

**Objective:** Replace the lookup table.

**Steps:**

1. `DeliveryStateMachine` finds the handler for the current state and asks it; remove the `EnumMap` table (or keep it only in tests as an oracle).
2. Extend `DeliveryStateMachineTest` with every illegal jump (e.g. CREATED → DELIVERED, DELIVERED → anything) → `InvalidStateTransitionException` → 409.

**Commit:** `refactor(delivery): delegate transitions to state classes`

**Done when:** All FSM tests pass.


#### Stage 4: Guards for terminal states

**Objective:** DELIVERED needs proof, FAILED needs a reason.

**Steps:**

1. `OutForDeliveryState.validate`: DELIVERED requires recipient name + photo or signature; FAILED requires a `DeliveryFailureReason`.
2. `ProofOfDeliveryGuardTest`.

**Commit:** `feat(delivery): require proof or reason for terminal states`

**Done when:** Tests pass.


#### Stage 5: Publish after commit

**Objective:** Status events only for committed changes.

**Steps:**

1. Publish `parcel.status.changed` (with `eventId`, `correlationId`, `trackingNumber`, previous/new public status) from an after-commit listener.

**Commit:** `fix(delivery): publish status change after commit`

**Done when:** A rejected transition emits nothing.


#### Stage 6: Event integration test

**Objective:** Prove the event leaves the service.

**Steps:**

1. `StatusEventPublishIT` (Testcontainers RabbitMQ + Postgres): valid transition → one message on a test queue bound to `parcel.status.changed.#` with the right tracking number.

**Commit:** `test(delivery): verify status change event publication`

**Done when:** IT passes.


### PR 2: Horizontal authorisation


#### Stage 7: Owner check helper

**Objective:** One place for the rule.

**Steps:**

1. `DeliveryAccessPolicy.assertCanAccess(session, caller)`: courier must match `session.courierId`; DISPATCHER/SYSTEM_ADMIN bypass.

**Commit:** `feat(delivery): add delivery access policy`

**Done when:** Unit test for both roles.


#### Stage 8: Apply to reads

**Objective:** Courier A can't view Courier B's parcels.

**Steps:**

1. Use the policy in get-by-id, by-order, by-tracking, breadcrumbs, POD get; `GET /courier/{courierId}` only when it equals the caller.

**Commit:** `feat(delivery): enforce courier ownership on reads`

**Done when:** Reading another courier's delivery returns 403.


#### Stage 9: Apply to writes

**Objective:** Courier A can't update Courier B's parcels.

**Steps:**

1. Use the policy in transition, location and POD upload/verify.
2. `CourierHorizontalAuthTest` (mandatory): A → 403 on GET and transition of B's delivery.

**Commit:** `feat(delivery): enforce courier ownership on updates`

**Done when:** Test passes.


#### Stage 10: Today's deliveries

**Objective:** Endpoint for the courier home screen.

**Steps:**

1. `GET /api/v1/deliveries/me?date=` (default today) for the caller, ordered by assignment time.

**Commit:** `feat(delivery): add current courier delivery list`

**Done when:** Returns only the caller's deliveries.


### PR 3: Proof of delivery upload


#### Stage 11: Multipart endpoint

**Objective:** Accept photo uploads.

**Steps:**

1. `POST /{id}/proof-of-delivery` as `multipart/form-data`: `photo` (≤ 5 MB, jpeg/png), `recipientName`, optional `signature`.

**Commit:** `feat(delivery): accept multipart proof of delivery`

**Done when:** Upload via Swagger succeeds.


#### Stage 12: File storage

**Objective:** Store files safely.

**Steps:**

1. Save to `POD_STORAGE_DIR` (env, mounted volume in compose) with a random UUID file name; store only the reference in DB; never use the client's file name.

**Commit:** `feat(delivery): store proof photos on configured volume`

**Done when:** File appears in the directory; DB row has the path.


#### Stage 13: Upload validation test

**Objective:** Reject bad files.

**Steps:**

1. `PodUploadTest`: > 5 MB or non-image → 400.

**Commit:** `test(delivery): validate proof of delivery uploads`

**Done when:** Test passes.


### PR 4: Courier mobile view


#### Stage 14: Today's list

**Objective:** Courier home screen.

**Steps:**

1. `/courier`: list from `/deliveries/me` with status badges; `touch` density, 56 px rows.

**Commit:** `feat(frontend): add courier today list`

**Done when:** Assigned parcels appear.


#### Stage 15: Delivery detail and next step

**Objective:** One big action per stage.

**Steps:**

1. `/courier/delivery/:id`: details + `ErgonomicActionBar` with the next allowed action; `SlideToTransitionBar` for irreversible steps; optimistic update with rollback on 409.

**Commit:** `feat(frontend): add step-by-step status actions`

**Done when:** Courier can move a parcel from Picked Up to Out for Delivery.


#### Stage 16: POD dialog

**Objective:** Capture proof.

**Steps:**

1. `ProofOfDeliveryCaptureModal`: `<input type="file" accept="image/*" capture="environment">` + recipient name; submit then transition to DELIVERED.

**Commit:** `feat(frontend): add proof of delivery capture`

**Done when:** Photo upload completes the delivery.


#### Stage 17: Failure form

**Objective:** Report failed deliveries.

**Steps:**

1. 'Report Delivery Failure' form with reason dropdown from `DeliveryFailureReason` + note.

**Commit:** `feat(frontend): add delivery failure report form`

**Done when:** Parcel moves to FAILED with the reason.


### PR 5: Documentation


#### Stage 18: Diagrams

**Objective:** Your assigned deliverable.

**Steps:**

1. Courier status update sequence and delivery state machine diagram (Part D) in `docs/design/m6/`.

**Commit:** `docs(delivery): add status update sequence and state machine`

**Done when:** Files committed and shared.

## Part C. Testing and security (summary)

Every test below is written and committed inside a stage in Part B. Use this table as a final checklist.

| Test | Type | What it proves |
|---|---|---|
| `CourierHorizontalAuthTest` | MockMvc | **Mandatory:** Courier A gets 403 on GET and transition of Courier B's delivery |
| `DeliveryStateMachineTest` (extend) | Unit, parameterised | Every illegal jump rejected (e.g. CREATED → DELIVERED, DELIVERED → anything) |
| `StateHandlersTest` | Unit | Each state class allows only its next states |
| `ProofOfDeliveryGuardTest` | Unit | DELIVERED without proof rejected; FAILED without reason rejected |
| `StatusEventPublishIT` | Integration (Testcontainers RabbitMQ) | A valid transition puts one `parcel.status.changed` message with the right tracking number on a bound test queue |
| `PodUploadTest` | MockMvc | >5 MB or non-image → 400 |

## Part D. Documentation deliverable and demo

1. *Sequence diagram:* courier taps "Delivered" → POD upload → FSM guard → DB → `parcel.status.changed` → gateway hub, notification, webhook, analytics.
2. *State machine diagram:* all states, legal transitions, guards on DELIVERED and FAILED.

**Your demo part:** on a phone-sized window, log in as the courier, move the parcel through each stage, take a POD photo, then show that Courier B can't open it (403) and that an illegal jump is rejected.

## Part E. Finishing up

**E1. Definition of done (tick every box before you call your part finished).**

- [ ] Every item in your "Gaps to close" checklist is done or has a written reason why not.
- [ ] `mvn -f backend\pom.xml -pl delivery-execution-service -am verify` passes locally.
- [ ] Coverage report exists for your module (once Member 1 adds JaCoCo: `backend/delivery-execution-service/target/site/jacoco/index.html`). Aim for **70%+ line coverage** on `service/` and `controller/`.
- [ ] Swagger docs for your endpoints are complete (`@Tag`, `@Operation`, `@Schema`) and `docs/api-specs` is updated.
- [ ] Frontend types regenerated with `npm run api:sync:specs` (commit only **your** service's generated file).
- [ ] `npm run lint` and `npm run build` pass in `frontend/`.
- [ ] DB and RabbitMQ credentials in `application.yml` come from env variables (no hard-coded values).
- [ ] No secrets in code, no personal data (phone, address, names) in logs or error messages.
- [ ] Your service block in `docker-compose.yml` starts and is healthy (`docker compose up -d delivery-execution-service`).
- [ ] `infra/k8s/services/delivery-execution-service.yml` has every env variable your `application.yml` reads (bonus marks).
- [ ] Documentation deliverable exported (PNG/PDF + source) to `docs/design/m6/` and sent to whoever compiles the final design PDF.

**E2. Test evidence for the submission.** Save `backend/delivery-execution-service/target/surefire-reports/` and the JaCoCo `index.html` (as a screenshot or PDF) into `docs/test-reports/m6/`.

**E3. Demo preparation.** Rehearse your part of the demo script (Part D) twice on a clean `docker compose down -v` start. Be ready to explain your code, your diagrams, and one security decision you made.

**E4. Contribution sheet line.** Send the team lead a 3-line summary: service, frontend screens, tests and diagrams you delivered, with links to your merged PRs.
