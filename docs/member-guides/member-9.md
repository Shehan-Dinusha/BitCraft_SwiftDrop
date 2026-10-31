# Member 9: Umesh · Notification Service & Message Broker Orchestration

> Draft guide, written from the `dev` branch of [Shehan-Dinusha/BitCraft_SwiftDrop](https://github.com/Shehan-Dinusha/BitCraft_SwiftDrop/tree/dev) as of 27 Oct 2026 (commit `1a05498`).
> Your assignment list is the source of truth for **what** you build. The repo is the source of truth for **how** (stack, folders, ports, names).

## 0. Your scope at a glance

| Item | Value |
|---|---|
| Backend module | `backend/notification-service` |
| Local app port | `8088` |
| Database | `notify-postgres` container, host port `5439`, database `swiftdrop_notify` |
| Frontend folders you own | new `frontend/src/features/notifications/` (`NotificationBell.tsx`, `NotificationTray.tsx`), `features/customer/components/NotificationPreferencesCard.tsx`, new `features/admin-ops/components/NotificationLogModal.tsx`, `src/services/api/notificationApi.ts` |
| Documentation deliverable | Publish/Subscribe integration design document + complete message payload schema specifications |
| Branch prefix | `feature/m9-<short-topic>` (off `dev`, PR back into `dev`) |

Read [README.md](README.md) in this folder once. It has the shared-file ownership map and the list of cross-member contracts.

## 1. What already exists on `dev`

- `NotificationEventConsumer` on `notification.events.queue` (Java binds `parcel.status.changed.#` and `notification.#`); `LifecycleNotificationConsumer` (empty shell).
- `MultiChannelNotificationDispatcher` (mock SMS/email/push, writes `notification_dispatch_logs`), `TemplateRendererService` (`{{placeholders}}`), `RecipientPreferenceService`.
- Templates seeded in `V2__expand_notification_schema.sql`: `PARCEL_CREATED`, `PARCEL_PICKED_UP`, `PARCEL_OUT_FOR_DELIVERY`, `PARCEL_DELIVERED`, `PARCEL_FAILED`.
- Controllers: `/api/v1/notifications/logs` (list, get, `POST /dispatch`), `/api/v1/notifications/preferences`.
- Shared broker assets: `infra/local/rabbitmq/definitions.json` (exchanges `swiftdrop.events.exchange`, `swiftdrop.dlx.exchange`, 18 queues), `common-contracts/events/*`, `EventRoutingKeys`.
- Six test classes. Frontend: `NotificationPreferencesCard`, `notificationApi.ts`.

## 2. Gaps to close

**Broker orchestration (you own this for the whole team):**

- [ ] **`definitions.json` doesn't match the Java configs.** Examples: `notification.events.queue` binds only `notification.#` in JSON but `parcel.status.changed.#` in Java; `webhook.events.queue` bindings differ; `customer-order.fee-revision.queue` and `swiftdrop.auth.events` are missing from JSON. Produce one table of every exchange/queue/binding, then make `definitions.json` and each `RabbitMQConfig` agree (send owners the exact lines for their config).
- [ ] **Contract changes requested by others** (all additive, nullable fields):
  - `CourierAssignedEvent`: add `trackingNumber`, `merchantId` (for Members 5 and 6).
  - New `MerchantApprovedEvent {eventId, merchantId, ownerUserId, approvedAt}` + key `merchant.approved` (Members 7 and 2).
- [ ] **Schema document** for every event (fields, types, example JSON, producer, consumers, routing key).

**Notification service:**

- [ ] **Subscribe to `order.created`** too (your list). Bind it on your queue.
- [ ] **Critical transitions only:** send email/SMS on Out for Delivery, Delivered, Failed (plus order confirmation). Depends on Member 6 adding `OUT_FOR_DELIVERY`.
- [ ] **Recipient contact:** events carry only `customerId`. Call Member 2's `GET /internal/v1/users/{id}/contact` (cache it), don't put phones/emails into events.
- [ ] **PII in logs:** `MultiChannelNotificationDispatcher` logs the destination in plain text. Mask it (`07X***5678`, `a***@gmail.com`).
- [ ] **In-app notification repository:** a per-user inbox (`in_app_notifications`: user id, title, body, tracking number, read flag) with `GET /api/v1/notifications/me`, `PATCH /me/{id}/read`, `POST /me/read-all`.
- [ ] Idempotency on `eventId`; send to DLQ after 3 consumer failures.

## 3. Boundaries

**You own:** `backend/notification-service/`; `common-contracts/events/**` and `EventRoutingKeys` (record files); `infra/local/rabbitmq/definitions.json`; `infra/k8s/messaging/`; the frontend notification files above.

**You do not touch:** other services' publishers/consumers. You send them the exact change for their `RabbitMQConfig`; they make it.

**Rule for contract changes:** additive only (new nullable fields, new events). Never rename or remove a field without every consumer agreeing. Announce each change and ask everyone to re-run step A4.

## 4. Who you depend on

| Member | What you need | Until it arrives |
|---|---|---|
| Member 6 (Kasun) | `OUT_FOR_DELIVERY` and public `FAILED` | Map from `internalSubStatus` |
| Member 2 (Nimsara) | Contact lookup endpoint | Use a fake contact (`customer-<id>@example.com`) |
| Member 1 (Shehan) | Header slot for `<NotificationBell/>` | Mount it in a local test page |
| Everyone | Review of the broker table | — |

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
git checkout -b feature/m9-setup
```

**A3. Environment file.**

```powershell
Copy-Item .env.example .env
```

Never commit `.env`. Every new variable you add goes into `.env.example`, `docker-compose.yml` and `infra/k8s/services/notification-service.yml` (rule from `AGENTS.md`).

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
docker compose up -d rabbitmq notify-postgres
docker compose ps
```

RabbitMQ UI: http://localhost:15672 (user `swiftdrop`, password `changeme_rabbit`).

**A7. Run your service and confirm it boots.**

```powershell
mvn -f backend\pom.xml -pl notification-service -am spring-boot:run
```

Open http://localhost:8088/actuator/health and http://localhost:8088/swagger-ui.html.

**A8. Record today's baseline.** Run your module's tests once and write down what passes and fails before you change anything:

```powershell
mvn -f backend\pom.xml -pl notification-service -am test
```

**A9. Read before coding:** `AGENTS.md`, `backend/AGENTS.md`, `frontend/AGENTS.md`, `docs/LOCAL-DEVELOPMENT.md`, `frontend/COMPONENTS.md`, and `.agents/rules/*.md`. They define package layout, DTO rules (Java records + `@Schema`), Flyway rules (new `V<N>__*.sql`, never edit an old one), and the Velocity design-system rules.

## Part B0. How you work every day (same for all members)

1. Start from fresh `dev`: `git checkout dev; git pull origin dev; git checkout -b feature/m9-<topic>`.
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
- Branch per PR: `feature/m9-<pr-topic>` from fresh `dev`.

> **Before you start:** `repo-fix-plan.md` (credentials, gateway URLs, compose, enum change) is being done separately. Pull `dev` once it's merged. You own the broker for the whole team, so PR 1 helps everyone; do it first.


### PR 1: Broker topology (team-wide)


#### Stage 1: Topology document

**Objective:** One table of every exchange, key, queue and consumer.

**Steps:**

1. Run `grep -rn "convertAndSend\|BindingBuilder\|@RabbitListener" backend` and list producer, routing key, queue, consumer, DLQ in `docs/integration/broker-topology.md`.

**Commit:** `docs(broker): document RabbitMQ topology`

**Done when:** Every `RabbitMQConfig` binding appears in the table.


#### Stage 2: definitions.json parity

**Objective:** Local broker matches the Java configs.

**Steps:**

1. Update `infra/local/rabbitmq/definitions.json`: add `parcel.status.changed.#` to `notification.events.queue`, the webhook bindings, `customer-order.fee-revision.queue`, `swiftdrop.auth.events`, and any queue missing.

**Commit:** `fix(broker): align definitions.json with service bindings`

**Done when:** `docker compose down -v; docker compose up -d rabbitmq` shows all queues and bindings in the UI.


#### Stage 3: Parity test

**Objective:** Stop the drift coming back.

**Steps:**

1. `DefinitionsJsonParityTest` in notification-service test sources: parse the JSON and assert a fixed list of required queues/bindings exists.

**Commit:** `test(broker): check definitions.json contains required bindings`

**Done when:** Test passes; removing a binding makes it fail.


#### Stage 4: CourierAssignedEvent fields

**Objective:** Members 5 and 6 need tracking number and merchant.

**Steps:**

1. Add nullable `trackingNumber`, `merchantId` to `CourierAssignedEvent` (additive only); rebuild contracts; announce 're-run A4'.

**Commit:** `feat(contracts): add tracking and merchant ids to courier assigned event`

**Done when:** Whole backend compiles.


#### Stage 5: MerchantApprovedEvent

**Objective:** Members 7 and 2 need the approval event.

**Steps:**

1. Add `MerchantApprovedEvent` record and `EventRoutingKeys.MERCHANT_APPROVED = "merchant.approved"`; queue `iam.merchant-approved.queue` in definitions.json.

**Commit:** `feat(contracts): add merchant approved event`

**Done when:** Contracts build; queue appears in RabbitMQ UI.


### PR 2: Observer-based consumer


#### Stage 6: Trigger interface

**Objective:** Observer Pattern for notifications.

**Steps:**

1. `NotificationTrigger` interface: `boolean matches(EventEnvelope)`, `List<NotificationRequest> build(EventEnvelope)`; consumer calls every matching trigger.

**Commit:** `refactor(notification): introduce notification trigger observers`

**Done when:** Existing tests pass.


#### Stage 7: Status triggers

**Objective:** Alerts on critical transitions.

**Steps:**

1. `OutForDeliveryTrigger`, `DeliveredTrigger`, `FailedTrigger` using the seeded templates.

**Commit:** `feat(notification): add triggers for out for delivery, delivered and failed`

**Done when:** Publishing a DELIVERED test event writes a dispatch log.


#### Stage 8: order.created trigger

**Objective:** Order confirmation message.

**Steps:**

1. Bind `order.created` to `notification.events.queue` (Java + definitions.json); `OrderCreatedTrigger` uses `PARCEL_CREATED`.
2. `OrderCreatedNotificationIT`.

**Commit:** `feat(notification): notify customers on order creation`

**Done when:** IT passes.


#### Stage 9: Idempotency

**Objective:** No duplicate alerts.

**Steps:**

1. Flyway `processed_events(event_id PK)`; skip seen events.
2. `DuplicateEventTest`.

**Commit:** `feat(notification): ignore duplicate events`

**Done when:** Same event twice → one log.


#### Stage 10: Broker integration test

**Objective:** Required test.

**Steps:**

1. `BrokerNotificationIT` (Testcontainers RabbitMQ + Postgres): publish mock DELIVERED event to the exchange → within 5 s a dispatch log exists.

**Commit:** `test(notification): verify broker-to-notification delivery`

**Done when:** IT passes.


### PR 3: Privacy


#### Stage 11: Contact lookup client

**Objective:** Get recipient contact without PII in events.

**Steps:**

1. `ContactClient` calling Member 2's `/internal/v1/users/{id}/contact` with a 10 min cache; fake contact fallback when `CONTACT_MODE=fake`.

**Commit:** `feat(notification): look up recipient contact from IAM`

**Done when:** Logs show a resolved (masked) recipient.


#### Stage 12: Mask recipients in logs

**Objective:** No PII in logs.

**Steps:**

1. Mask phone (`07X***5678`) and email (`a***@gmail.com`) everywhere `MultiChannelNotificationDispatcher` logs; `MaskingTest`.

**Commit:** `fix(notification): mask recipients in logs`

**Done when:** Grep the log: no full numbers.


#### Stage 13: Template PII test

**Objective:** Required template safety test.

**Steps:**

1. `TemplatePiiTest`: rendered output never contains passwords, card numbers, full address or full phone; unknown placeholders render empty.

**Commit:** `test(notification): ensure templates don't leak personal data`

**Done when:** Test passes.


### PR 4: In-app notifications


#### Stage 14: Inbox table

**Objective:** Store per-user notifications.

**Steps:**

1. Flyway `in_app_notifications(id, user_id, title, body, tracking_number, read, created_at)`; triggers also write here (customer and, for merchant orders, the merchant owner).

**Commit:** `feat(notification): store in-app notifications`

**Done when:** Status change creates an inbox row.


#### Stage 15: Inbox endpoints

**Objective:** Logged-in users read their history.

**Steps:**

1. `GET /api/v1/notifications/me`, `PATCH /me/{id}/read`, `POST /me/read-all`; scoped to `X-User-Id`.

**Commit:** `feat(notification): add in-app notification endpoints`

**Done when:** User only sees their own items.


### PR 5: Frontend


#### Stage 16: Notification bell

**Objective:** Unread count in the header.

**Steps:**

1. Implement `features/notifications/NotificationBell.tsx` (Member 1's slot) with unread count, polling every 20 s.

**Commit:** `feat(frontend): add notification bell`

**Done when:** Count rises after a status change.


#### Stage 17: Sliding tray

**Objective:** Read notifications.

**Steps:**

1. `NotificationTray` drawer: list, mark read, link to `/track/<number>`.

**Commit:** `feat(frontend): add notification tray`

**Done when:** Marking read lowers the count.


#### Stage 18: Mock SMS/email log viewer

**Objective:** Show examiners notifications fired.

**Steps:**

1. `NotificationLogModal` on the dispatcher dashboard / `/ops/notifications`: masked recipient, template, time, status.

**Commit:** `feat(frontend): add notification log viewer for dispatchers`

**Done when:** Logs appear after a demo status change.


### PR 6: Documentation


#### Stage 19: Pub/sub design and schemas

**Objective:** Your assigned deliverable.

**Steps:**

1. Publish/Subscribe integration design and per-event payload schema spec (Part D) in `docs/design/m9/`.

**Commit:** `docs(broker): add pub/sub design and event schemas`

**Done when:** Files committed and shared.

## Part C. Testing and security (summary)

Every test below is written and committed inside a stage in Part B. Use this table as a final checklist.

| Test | Type | What it proves |
|---|---|---|
| `BrokerNotificationIT` | Integration (Testcontainers RabbitMQ + Postgres) | **Required:** publish a mock `parcel.status.changed` (DELIVERED) to the exchange; within 5 s a dispatch log and an in-app row exist |
| `OrderCreatedNotificationIT` | Integration | `order.created` produces the confirmation notification |
| `TemplatePiiTest` | Unit | **Required:** rendered SMS/email never contains password, payment card, full address or full phone; unknown placeholders render empty |
| `MaskingTest` | Unit | Phone and email masking |
| `DuplicateEventTest` | Unit | Same `eventId` twice → one notification |
| `DefinitionsJsonParityTest` | Unit | Parses `definitions.json` and checks every queue/binding declared in Java configs exists in it |

## Part D. Documentation deliverable and demo

1. *Publish/Subscribe integration design:* why a topic exchange, list of routing keys, producers/consumers, DLX/DLQ strategy, idempotency, ordering assumptions. (You already have `m9-use-case` and `m9-sequence` in project files.)
2. *Message payload schema spec:* one section per event with field table and example JSON.

**Your demo part:** after a status change, open the bell to show the new notification, then open the dispatcher's log modal to show the mock SMS/email with masked recipient, and the message counts in the RabbitMQ UI.

## Part E. Finishing up

**E1. Definition of done (tick every box before you call your part finished).**

- [ ] Every item in your "Gaps to close" checklist is done or has a written reason why not.
- [ ] `mvn -f backend\pom.xml -pl notification-service -am verify` passes locally.
- [ ] Coverage report exists for your module (once Member 1 adds JaCoCo: `backend/notification-service/target/site/jacoco/index.html`). Aim for **70%+ line coverage** on `service/` and `controller/`.
- [ ] Swagger docs for your endpoints are complete (`@Tag`, `@Operation`, `@Schema`) and `docs/api-specs` is updated.
- [ ] Frontend types regenerated with `npm run api:sync:specs` (commit only **your** service's generated file).
- [ ] `npm run lint` and `npm run build` pass in `frontend/`.
- [ ] DB and RabbitMQ credentials in `application.yml` come from env variables (no hard-coded values).
- [ ] No secrets in code, no personal data (phone, address, names) in logs or error messages.
- [ ] Your service block in `docker-compose.yml` starts and is healthy (`docker compose up -d notification-service`).
- [ ] `infra/k8s/services/notification-service.yml` has every env variable your `application.yml` reads (bonus marks).
- [ ] Documentation deliverable exported (PNG/PDF + source) to `docs/design/m9/` and sent to whoever compiles the final design PDF.

**E2. Test evidence for the submission.** Save `backend/notification-service/target/surefire-reports/` and the JaCoCo `index.html` (as a screenshot or PDF) into `docs/test-reports/m9/`.

**E3. Demo preparation.** Rehearse your part of the demo script (Part D) twice on a clean `docker compose down -v` start. Be ready to explain your code, your diagrams, and one security decision you made.

**E4. Contribution sheet line.** Send the team lead a 3-line summary: service, frontend screens, tests and diagrams you delivered, with links to your merged PRs.
