# Member 5: Sehan · Dispatcher & Courier Assignment

> Draft guide, written from the `dev` branch of [Shehan-Dinusha/BitCraft_SwiftDrop](https://github.com/Shehan-Dinusha/BitCraft_SwiftDrop/tree/dev) as of 27 Oct 2026 (commit `1a05498`).
> Your assignment list is the source of truth for **what** you build. The repo is the source of truth for **how** (stack, folders, ports, names).

## 0. Your scope at a glance

| Item | Value |
|---|---|
| Backend module | `backend/dispatch-assignment-service` |
| Local app port | `8084` |
| Database | `dispatch-postgres` container, host port `5435`, database `swiftdrop_dispatch` |
| Frontend folders you own | new `frontend/src/features/admin-ops/pages/DispatchConsolePage.tsx`, `features/admin-ops/components/ManualDispatchOverrideModal.tsx`, `LiveFleetHeatmap.tsx`, `src/services/api/dispatchApi.ts` |
| Documentation deliverable | State transition diagram of parcel assignment + sequence diagram for dispatcher allocation |
| Branch prefix | `feature/m5-<short-topic>` (off `dev`, PR back into `dev`) |

Read [README.md](README.md) in this folder once. It has the shared-file ownership map and the list of cross-member contracts.

## 1. What already exists on `dev`

- Consumers: `OrderCreatedConsumer` (`dispatch.order-created.queue` ← `order.created`), `OrderCancelledConsumer`, `ParcelStatusSyncConsumer` (`parcel.status.changed.#`).
- `DispatchController` `/api/v1/dispatch`: `POST /assign`, `GET /assignments`, `/assignments/{id}`, `/assignments/order/{orderId}`, `GET /unassigned`, `GET /couriers/{courierId}/assignments`.
- `CourierAvailabilityController` `/api/v1/dispatch/couriers`: list, get, shifts start/end/status/location.
- `CourierMatchingEngine` (picks lowest workload), `ManualOverrideService`, `DispatchEventPublisher` → `dispatch.courier.assigned`, `dispatch.failed`, `parcel.status.changed`.
- Entities `DispatchAssignment`, `CourierShift`; Flyway V1, V2. Six test classes.
- Frontend: `FleetControlTowerPage`, `ManualDispatchOverrideModal`, `LiveFleetHeatmap`, `dispatchApi.ts`.

## 2. Gaps to close

- [ ] **No optimistic locking.** There is no `@Version` anywhere. Add `@Version` to `DispatchAssignment` (and the order-queue row) so two dispatchers can't assign the same parcel. Return `409 Conflict` on `OptimisticLockingFailureException`.
- [ ] **Idempotent consumer.** `order.created` may be delivered twice; store `eventId` or use a unique key on `orderId`.
- [ ] **Event name.** Your list says `courier.assigned`; the repo uses `dispatch.courier.assigned` (constant `EventRoutingKeys.DISPATCH_COURIER_ASSIGNED`) and Member 6 already consumes it. **Keep the repo name**, mention it in your docs.
- [ ] **`CourierAssignedEvent` lacks `trackingNumber` and `merchantId`.** Member 6 needs the tracking number to publish public status events. Ask Member 9 to add both fields (additive), then fill them from your stored `order.created` data.
- [ ] **Live workload indicator** ("Courier A: 4 active parcels"): an endpoint returning active count per courier, updated when assignments and terminal status events arrive.
- [ ] **Semi-automated suggestions:** `GET /api/v1/dispatch/orders/{orderId}/suggestions` returning top 3 couriers by lowest active load (and same district).
- [ ] **RBAC:** assignment endpoints only for `DISPATCHER`/`SYSTEM_ADMIN`; a courier may only read their own assignments.
- [ ] **Courier identities.** Couriers are users in Member 2's IAM. Decide how a courier appears in your shift table (on first shift start, using `X-User-Id`).
- [ ] Dispatcher Management Console UI.

## 3. Boundaries

**You own:** `backend/dispatch-assignment-service/`; the dispatcher console files above.

**You do not touch:** delivery FSM (Member 6) or the courier mobile view; event record files (ask Member 9).

## 4. Who you depend on

| Member | What you need | Until it arrives |
|---|---|---|
| Member 3 (Dulanga) | `order.created` (already published) | Publish a sample message from the RabbitMQ UI |
| Member 9 (Umesh) | `trackingNumber` + `merchantId` added to `CourierAssignedEvent` | Keep sending the current record |
| Member 6 (Kasun) | `parcel.status.changed` with terminal states so you can decrement workload | Decrement on `DELIVERED`/`FAILED`/`CANCELLED` of current enum |
| Member 2 (Nimsara) | `CallerContext`; a way to list couriers (`GET /api/v1/users?role=COURIER`) for the console | Use your shift table as the courier list |

Others depend on you for: `dispatch.courier.assigned` (Member 6 creates the delivery session from it), workload data for Member 10's charts, and the shift API that Member 6's courier screen calls.

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
git checkout -b feature/m5-setup
```

**A3. Environment file.**

```powershell
Copy-Item .env.example .env
```

Never commit `.env`. Every new variable you add goes into `.env.example`, `docker-compose.yml` and `infra/k8s/services/dispatch-assignment-service.yml` (rule from `AGENTS.md`).

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
docker compose up -d rabbitmq dispatch-postgres
docker compose ps
```

RabbitMQ UI: http://localhost:15672 (user `swiftdrop`, password `changeme_rabbit`).

**A7. Run your service and confirm it boots.**

```powershell
mvn -f backend\pom.xml -pl dispatch-assignment-service -am spring-boot:run
```

Open http://localhost:8084/actuator/health and http://localhost:8084/swagger-ui.html.

**A8. Record today's baseline.** Run your module's tests once and write down what passes and fails before you change anything:

```powershell
mvn -f backend\pom.xml -pl dispatch-assignment-service -am test
```

**A9. Read before coding:** `AGENTS.md`, `backend/AGENTS.md`, `frontend/AGENTS.md`, `docs/LOCAL-DEVELOPMENT.md`, `frontend/COMPONENTS.md`, and `.agents/rules/*.md`. They define package layout, DTO rules (Java records + `@Schema`), Flyway rules (new `V<N>__*.sql`, never edit an old one), and the Velocity design-system rules.

## Part B0. How you work every day (same for all members)

1. Start from fresh `dev`: `git checkout dev; git pull origin dev; git checkout -b feature/m5-<topic>`.
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
- Branch per PR: `feature/m5-<pr-topic>` from fresh `dev`.

> **Before you start:** `repo-fix-plan.md` (credentials, gateway URLs, compose) is being done separately. Pull `dev` once it's merged.


### PR 1: Correctness of the queue


#### Stage 1: Version columns

**Objective:** Prepare optimistic locking.

**Steps:**

1. Flyway `V3__add_version_columns.sql`: `version BIGINT NOT NULL DEFAULT 0` on `dispatch_assignments` (and the unassigned-queue table if separate); add `@Version` to the entities.

**Commit:** `feat(dispatch): add optimistic locking version columns`

**Done when:** Service boots; existing tests pass.


#### Stage 2: 409 on concurrent assignment

**Objective:** Two dispatchers can't assign the same parcel.

**Steps:**

1. Wrap assign in a transaction; catch `OptimisticLockingFailureException` and return 409 via `GlobalExceptionHandler`.

**Commit:** `feat(dispatch): return conflict on concurrent assignment`

**Done when:** Manual test with two quick requests: one 200, one 409.


#### Stage 3: Concurrency test

**Objective:** Required race-condition test.

**Steps:**

1. `ConcurrentAssignmentIT` (Testcontainers Postgres): two threads assign the same parcel → exactly one success, one 409, one row in DB.

**Commit:** `test(dispatch): verify concurrent assignment is rejected`

**Done when:** IT passes repeatedly (run 5 times).


#### Stage 4: Idempotent order.created

**Objective:** Duplicate messages don't create duplicate queue rows.

**Steps:**

1. Unique constraint on `order_id` (Flyway V4); `OrderCreatedConsumer` ignores duplicates.

**Commit:** `fix(dispatch): ignore duplicate order.created events`

**Done when:** Publishing the same message twice in RabbitMQ UI leaves one row.


#### Stage 5: Consumer integration test

**Objective:** Required test for the subscription.

**Steps:**

1. `OrderCreatedConsumerIT` (Testcontainers RabbitMQ + Postgres): message on the exchange lands in `dispatch.order-created.queue` and creates one unassigned row; duplicate ignored.

**Commit:** `test(dispatch): verify order.created consumer subscription`

**Done when:** IT passes.


#### Stage 6: Explicit queue states

**Objective:** Clear assignment lifecycle.

**Steps:**

1. Use states `UNASSIGNED → ASSIGNED → REASSIGNED → COMPLETED | CANCELLED` in `AssignmentStatus`; transitions in one service method.

**Commit:** `refactor(dispatch): make assignment states explicit`

**Done when:** Unit tests for allowed transitions pass.


### PR 2: Workload and suggestions


#### Stage 7: Workload projection

**Objective:** Active parcels per courier.

**Steps:**

1. Count assignments in ASSIGNED/REASSIGNED per courier; decrement on terminal `parcel.status.changed` (DELIVERED, FAILED, CANCELLED).
2. `WorkloadProjectionTest`.

**Commit:** `feat(dispatch): track active workload per courier`

**Done when:** Test passes.


#### Stage 8: Workload endpoint

**Objective:** Console can show 'Courier A: 4 active'.

**Steps:**

1. `GET /api/v1/dispatch/couriers/workload` → `[{courierId, name, active, district, shiftStatus}]`.

**Commit:** `feat(dispatch): expose courier workload endpoint`

**Done when:** Endpoint returns counts that change after an assignment.


#### Stage 9: Suggestions endpoint

**Objective:** Semi-automated assignment.

**Steps:**

1. `GET /api/v1/dispatch/orders/{orderId}/suggestions` → top 3 couriers by lowest active load, same district first (reuse `CourierMatchingEngine`).
2. Extend `CourierMatchingEngineTest` for ties.

**Commit:** `feat(dispatch): suggest couriers by lowest workload`

**Done when:** Suggestions list the least-busy courier first.


#### Stage 10: Publish after commit

**Objective:** No event for a rolled-back assignment.

**Steps:**

1. Publish `dispatch.courier.assigned` from a `@TransactionalEventListener(AFTER_COMMIT)`.
2. Once Member 9 adds the fields, fill `trackingNumber` and `merchantId` from your stored order data.

**Commit:** `fix(dispatch): publish courier assigned after commit`

**Done when:** A failed assignment emits no message.


### PR 3: Security


#### Stage 11: Dispatcher-only writes

**Objective:** Only dispatchers/admins assign.

**Steps:**

1. Guard assign, reassign and suggestions with `requireRole(DISPATCHER, SYSTEM_ADMIN)`.

**Commit:** `feat(dispatch): restrict assignment to dispatchers`

**Done when:** Courier token → 403 on `/assign`.


#### Stage 12: Courier sees only own assignments

**Objective:** Horizontal check.

**Steps:**

1. `GET /couriers/{courierId}/assignments`: allowed when `courierId == X-User-Id` or dispatcher/admin.
2. `DispatchRbacTest` for both rules.

**Commit:** `feat(dispatch): limit couriers to their own assignments`

**Done when:** Test passes.


### PR 4: Dispatcher console


#### Stage 13: Unassigned table

**Objective:** See the queue.

**Steps:**

1. `DispatchConsolePage` at `/ops/dispatch`: `HighDensityDataGrid` of unassigned parcels (tracking number, pickup district, speed, age).

**Commit:** `feat(frontend): add unassigned parcels table`

**Done when:** New orders appear in the table.


#### Stage 14: Courier workload list

**Objective:** Live workload indicators.

**Steps:**

1. Side panel listing couriers with 'N active' badges; poll every 10 s.

**Commit:** `feat(frontend): show live courier workload`

**Done when:** Badge changes within 10 s of an assignment.


#### Stage 15: Assign modal

**Objective:** Allocate parcels.

**Steps:**

1. `ManualDispatchOverrideModal` with suggestions first and a dropdown of all couriers; on 409 refresh the row and show 'already assigned'.

**Commit:** `feat(frontend): add assign courier modal with conflict handling`

**Done when:** Assigning from two browser tabs shows the conflict message in the second.


### PR 5: Documentation


#### Stage 16: Diagrams

**Objective:** Your assigned deliverable.

**Steps:**

1. Assignment state transition diagram and dispatcher allocation sequence (Part D) in `docs/design/m5/`.

**Commit:** `docs(dispatch): add assignment state and allocation diagrams`

**Done when:** Files committed and shared.

## Part C. Testing and security (summary)

Every test below is written and committed inside a stage in Part B. Use this table as a final checklist.

| Test | Type | What it proves |
|---|---|---|
| `ConcurrentAssignmentIT` | Integration (Testcontainers Postgres) | Two threads assign the same parcel at once → exactly one 200, one 409; DB has one assignment |
| `OrderCreatedConsumerIT` | Integration (Testcontainers RabbitMQ) | Publishing `order.created` to the exchange lands in `dispatch.order-created.queue` and creates one unassigned row; a duplicate does not create a second |
| `CourierMatchingEngineTest` (extend) | Unit | Lowest active load wins; ties broken by district |
| `DispatchRbacTest` | MockMvc | Courier → 403 on `/assign`; courier A → 403 on courier B's assignments |
| `WorkloadProjectionTest` | Unit | Counts go up on assign and down on delivered/failed |

## Part D. Documentation deliverable and demo

1. *State transition diagram of parcel assignment:* UNASSIGNED → ASSIGNED → REASSIGNED → COMPLETED / CANCELLED, with the events that cause each.
2. *Sequence diagram for dispatcher allocation:* `order.created` → queue → dispatcher opens console → suggestions → assign (optimistic lock) → `dispatch.courier.assigned` → delivery-execution.

**Your demo part:** show the new order in the unassigned queue, assign it to the least-busy courier, show the workload badge change, and show a second dispatcher getting "already assigned".

## Part E. Finishing up

**E1. Definition of done (tick every box before you call your part finished).**

- [ ] Every item in your "Gaps to close" checklist is done or has a written reason why not.
- [ ] `mvn -f backend\pom.xml -pl dispatch-assignment-service -am verify` passes locally.
- [ ] Coverage report exists for your module (once Member 1 adds JaCoCo: `backend/dispatch-assignment-service/target/site/jacoco/index.html`). Aim for **70%+ line coverage** on `service/` and `controller/`.
- [ ] Swagger docs for your endpoints are complete (`@Tag`, `@Operation`, `@Schema`) and `docs/api-specs` is updated.
- [ ] Frontend types regenerated with `npm run api:sync:specs` (commit only **your** service's generated file).
- [ ] `npm run lint` and `npm run build` pass in `frontend/`.
- [ ] DB and RabbitMQ credentials in `application.yml` come from env variables (no hard-coded values).
- [ ] No secrets in code, no personal data (phone, address, names) in logs or error messages.
- [ ] Your service block in `docker-compose.yml` starts and is healthy (`docker compose up -d dispatch-assignment-service`).
- [ ] `infra/k8s/services/dispatch-assignment-service.yml` has every env variable your `application.yml` reads (bonus marks).
- [ ] Documentation deliverable exported (PNG/PDF + source) to `docs/design/m5/` and sent to whoever compiles the final design PDF.

**E2. Test evidence for the submission.** Save `backend/dispatch-assignment-service/target/surefire-reports/` and the JaCoCo `index.html` (as a screenshot or PDF) into `docs/test-reports/m5/`.

**E3. Demo preparation.** Rehearse your part of the demo script (Part D) twice on a clean `docker compose down -v` start. Be ready to explain your code, your diagrams, and one security decision you made.

**E4. Contribution sheet line.** Send the team lead a 3-line summary: service, frontend screens, tests and diagrams you delivered, with links to your merged PRs.
