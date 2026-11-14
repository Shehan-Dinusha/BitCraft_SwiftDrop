# Demo data seeder

Fills SwiftDrop with realistic demo data **through the public APIs**, the way real users would:

| Who | What the seeder does as them |
|---|---|
| System admin (bootstrap account) | Creates 25 dispatchers (one per district) and 50 couriers (two per district) |
| 40 customers | Register themselves, then book 4–6 parcels each (≈200 orders, real towns and postcodes) |
| Couriers | Start their shifts |
| Customers | Cancel ~10% of parcels before dispatch |
| Dispatchers | Assign ~75% of parcels to a courier in the pickup district; ~15% stay unassigned so the board has work |
| Couriers | Move parcels along: ~50% delivered (photo + OTP proof of delivery), ~10% out for delivery, ~10% failed with a reason, the rest picked up / in transit |
| Customers | Read the older ~60% of their in-app notifications |

Nothing is written with SQL. Dispatch, delivery, notification and analytics fill themselves from the
RabbitMQ events these actions publish, so every database stays consistent with the others.

All people are fictional. E-mail addresses use the reserved `example.com` domain, and SMS/e-mail
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
| `orders` | order, pricing | Bookings; dispatch is off, so `order.created` events queue up |
| `assign` | order, dispatch | Dispatch files the bookings as unassigned, then shifts, cancellations, assignments |
| `deliver` | delivery | Courier transitions and proof-of-delivery uploads |
| `catchup` | order, dispatch, notification, analytics | They process the queued events; notifications are marked read; summary |

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

## A cluster that runs everything (AKS)

Every service runs at once, so no switching is needed. Open a port-forward or use the public API URL:

```bash
SEED_API=https://api.swiftdrop.bitcraft.biz \
SEED_ADMIN_PASSWORD='<bootstrap admin password>' SEED_PASSWORD='Demo-pass-2026' \
node scripts/seed/seed.mjs all
```

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
`courier.<district>.1` and `.2`, and the customer usernames printed by the summary stage.
