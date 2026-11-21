# Demo data seeder

Fills SwiftDrop with realistic demo data **through the public APIs**, the way real users would:

| Who | What the seeder does as them |
|---|---|
| System admin (bootstrap account) | Creates 25 dispatchers (one per district), 50 couriers (two per district), 40 customers and 6 merchant owners |
| 40 customers | Book 4–6 parcels each (≈200 orders, real towns and postcodes) |
| 6 merchant businesses | Apply, and the admin approves all 6 (their e-mail addresses are not real, so decision e-mails could not be read). Each creates an API key and sends 8–12 B2B orders (≈58) through the B2B API. To demo the review flow, sign up a business with a real address at `/register/business` and approve it as that district's dispatcher |
| Couriers | Are created ready to work (active, no invite), register their own vehicle, get capacity 20 from their dispatcher, and start their shifts. To demo the invite flow, register a courier with a real address as a dispatcher on **Couriers** |
| Customers | Cancel ~10% of parcels before dispatch |
| Dispatchers | Assign ~80% of parcels to a courier in the pickup district; ~10% stay unassigned so the board has work |
| Couriers | Move parcels along: ~58% delivered (photo proof of delivery), ~7% out for delivery, ~10% failed with a reason, the rest picked up / in transit |
| Customers | Read the older ~60% of their in-app notifications |

Nothing is written with SQL. Dispatch, delivery, notification and analytics fill themselves from the
RabbitMQ events these actions publish, so every database stays consistent with the others.

Accounts are provisioned by the admin: since email verification was added, a self-registered
account cannot sign in until it confirms a code sent by e-mail, which a script cannot read.
Admin-provisioned accounts are trusted and verified from the start.

All people and businesses are fictional. E-mail addresses use the reserved `example.com` domain, and SMS/e-mail
sending is mocked, so nothing can reach a real person. Google sign-in cannot be scripted. Every seeded
account uses a username and password; test Google sign-in by hand with a real Google account.

## Local cluster (Docker Desktop)

A 4-CPU laptop cannot run all ten Java services at once, so `run-local.sh` switches services on only
for the stages that need them. Events published while a consumer is off wait in RabbitMQ's durable
queues (24-hour TTL) and are processed when it starts again.

```bash
# 1. Images for every service (once): scripts/k8s-local-build.sh
# 2. Seed. Pick the password every seeded account will use:
SEED_PASSWORD='Demo-pass-2026' scripts/seed/run-local.sh
```

It takes 20–40 minutes on a laptop. The stages, which you can also run one at a time:

| Stage | Services running (besides gateway + auth) | What happens |
|---|---|---|
| `install` | none | Installs the chart with `values-local-seed.yaml`: all services and databases created, most scaled to zero |
| `warmup` | each consumer, briefly | Makes sure every consumer queue exists (events sent to a missing queue are lost) |
| `users` | none | Staff accounts and customer sign-ups |
| `orders` | order, pricing (+ merchant) | Customer bookings, then merchant applications, approvals, API keys and B2B orders; dispatch is off, so `order.created` events queue up |
| `assign` | order, dispatch | Dispatch files the bookings as unassigned, then shifts, cancellations, assignments |
| `deliver` | delivery | Courier transitions and proof-of-delivery uploads |
| `catchup` | order, dispatch, notification, analytics | They process the queued events; notifications are marked read; summary |
| `timeshift` | none (databases only) | Spreads the history over the past 30 days; see below |

Re-running is safe: existing users are skipped, customers are topped up to their planned order count,
and parcels only move forward. If a stage fails, fix the cause and run that stage again, for example
`SEED_PASSWORD=… scripts/seed/run-local.sh deliver`. Finish all stages within 24 hours, or queued
events start to expire.

When it finishes, go back to the everyday local profile (the data stays in the volumes):

```bash
helm upgrade swiftdrop infra/helm/swiftdrop -n default \
  -f infra/helm/swiftdrop/values-local.yaml -f infra/helm/swiftdrop/values-secrets.yaml
```

To start again from empty, delete the release **and** the volumes: `helm uninstall swiftdrop -n default`,
then `kubectl delete pvc --all -n swiftdrop-data`, `-n swiftdrop-messaging` and `-n swiftdrop-apps`.

## A month of history (time-shift)

Every event is stamped "now", so a fresh seed puts the whole history on one day and the analytics
charts show a single spike. `timeshift.mjs` runs **once, after the last stage**, and spreads it out:

- each order's timeline is rebuilt step by step with realistic durations:

  | Step | Duration |
  |---|---|
  | booked → courier assigned | 10 min – 3 h (dispatchers work 07:00–21:00) |
  | assigned → picked up | 30 min – 4 h |
  | picked up → in transit (to the hub) | 15 – 90 min |
  | in transit → out for delivery | express 1–4 h; same district 2–6 h; other district: overnight, out next morning 08:00–10:00 |
  | out for delivery → delivered / failed | 30 min – 5 h / 1 – 6 h |
  | booked → cancelled | 10 min – 4 h |

  Courier steps happen 08:00–19:30 Colombo time; bookings 07:00–23:00. Finished orders are booked
  0–29 days ago (more in recent days, so charts show growth); orders still in progress end a
  realistic time before now (a parcel is never "out for delivery" for days);
- rows that stand for a step take that step's new time in every database: delivery's audit
  trail and pickup/delivered times, the proof photo (minutes before delivery), dispatch's
  assignment time, analytics events and projection fields, and notifications; any other
  timestamp is placed between the new step times;
- merchants get a story built backwards from their first B2B order: the owner signs up, the business
  applies, the admin approves it in office hours, an API key is created, then the orders arrive; the
  pending application is a day or two old and the rejected one a couple of weeks;
- the analytics daily and per-courier totals are rebuilt with the analytics service's own rules;
  seeded accounts get believable sign-up dates.

It only touches seeded data (`@example.com` customers and merchants), refuses to run a second time, and edits
demo data directly in the service databases through `kubectl exec` (no schema or code changes).

```bash
node scripts/seed/timeshift.mjs --dry-run   # show the planned spread, change nothing
node scripts/seed/timeshift.mjs             # apply (run-local.sh does this as its last stage)
```

## A cluster that runs everything (AKS)

Every service runs at once, so no switching is needed. Open a port-forward or use the public API URL.

> [!IMPORTANT]
> **Switch e-mail to mock while seeding.** When `secrets.acsEmail` is set, notification-service
> e-mails the customer at every order step. The seed creates about 240 orders for
> `@example.com` addresses, which cannot receive mail: that is roughly 700–900 bounced e-mails.
> They use up the Azure managed domain's small sending quota, and a high bounce rate can get ACS
> sending throttled or suspended, which would also stop real sign-up verification codes.
> In-app notifications are unaffected by the switch.

```bash
# 1. Mock e-mail for the duration of the seed (overrides the chart's value).
kubectl set env deploy/notification-service -n swiftdrop-apps NOTIFICATION_EMAIL_PROVIDER=mock
kubectl rollout status deploy/notification-service -n swiftdrop-apps

# 2. Seed.
SEED_API=https://api.swiftdrop.bitcraft.biz \
SEED_ADMIN_PASSWORD='<bootstrap admin password>' SEED_PASSWORD='Demo-pass-2026' \
node scripts/seed/seed.mjs all
node scripts/seed/timeshift.mjs     # needs kubectl pointed at the AKS cluster

# 3. Wait until notification-service has consumed every seed event (messages = 0) ...
kubectl exec -n swiftdrop-messaging rabbitmq-0 -- \
  rabbitmqctl list_queues name messages | grep notification.events.queue

# 4. ... then remove the override so the chart's real e-mail setting applies again.
kubectl set env deploy/notification-service -n swiftdrop-apps NOTIFICATION_EMAIL_PROVIDER-
kubectl rollout status deploy/notification-service -n swiftdrop-apps
```

Do step 4 only after step 3 shows `0`: events still queued when e-mail is switched back are sent
as real e-mails. Always run step 4 when you finish, and don't run a CD deploy in the middle of a
seed. Seeded accounts are created by the admin and start verified, so the seed itself never
needs an e-mailed code (as long as `AUTH_MFA_ENABLED` stays `false`).

## Proof-of-delivery photos

Put 6–8 JPEG or PNG photos of parcels at doorsteps in `scripts/seed/assets/` (under 300 KB each,
photos your team took, so there is no licensing question). Without them the seeder generates simple
placeholder images.

## Settings

| Variable | Default | Meaning |
|---|---|---|
| `SEED_PASSWORD` | (required) | Password for every seeded account; at least 8 characters with a letter and a digit |
| `SEED_ADMIN_PASSWORD` | read from `values-secrets.yaml` by `run-local.sh` | Bootstrap admin password |
| `SEED_ADMIN_USERNAME` | `admin` | Bootstrap admin username |
| `SEED_API` | `http://127.0.0.1:18080` | Gateway URL (`run-local.sh` port-forwards the gateway there) |
| `SEED_CUSTOMERS` | `40` | Number of customers |

Demo logins: `dispatcher.<district>` (for example `dispatcher.colombo`, `dispatcher.nuwara-eliya`),
`courier.<district>.1` and `.2`, `merchant.kandy-valley-spice` (all six merchants are approved; the summary lists them),
and the customer usernames printed by the summary stage.
