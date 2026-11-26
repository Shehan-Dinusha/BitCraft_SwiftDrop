# SwiftDrop operations runbook

How to release `dev` to production, keep the live AKS cluster healthy, and recover when something goes wrong. It is written for whoever is on duty, human or AI agent.

This page holds the procedures and the decisions behind them. Setup from scratch, every command's details and the full troubleshooting table are in the [AKS Helm deployment guide](AKS-HELM-DEPLOYMENT.md). The scripts it calls have usage notes at the top of each file.

## The environment at a glance

| Thing | Value |
|---|---|
| Azure subscription | Azure for Students (about 4 vCPU per region, limited credit) |
| Resource group / region | `rg-swiftdrop-prod` / `indiasouthcentral` |
| Cluster | AKS `swiftdrop-aks`, one `Standard_B4ms` node, student profile (`values-student.yaml`) |
| Image registry | ACR, name in the GitHub variable `ACR_NAME` |
| Helm release | `swiftdrop` in namespace `default`; workloads in `swiftdrop-apps`, `swiftdrop-data`, `swiftdrop-messaging` |
| Public URLs | https://swiftdrop.bitcraft.biz (app) · https://api.swiftdrop.bitcraft.biz (gateway, Swagger at `/swagger-ui.html`) |
| Add-ons | Traefik (ingress), cert-manager (Let's Encrypt), local-path (node-disk volumes) |
| Deploys | GitHub Actions [`cd-aks-deploy.yml`](../../.github/workflows/cd-aks-deploy.yml) on push to `main`, with approval on the `production` environment |

The scripts default to these names. Override them with `AKS_RESOURCE_GROUP` and `AKS_CLUSTER` if they ever change.

## Five rules that prevent most incidents

1. **The databases live on the node's own disk.** They survive restarts, deploys and `helm uninstall`, but not a node replacement. `az aks start` after a stop has replaced the node before (19 Nov 2026), and so can an upgrade, a node-pool scale or an Azure repair. There are no automatic backups.
2. **Back up before anything risky.** That means before a release, a pause, a cluster upgrade and every demo: `bash scripts/db/backup-aks.sh`.
3. **Flyway migrations only go forward.** `helm rollback` restores the old pods, not the old schema. If a release drops or rewrites data, the backup from rule 2 is the only way back.
4. **Know which cluster `kubectl` points at.** Your laptop can hold both `swiftdrop-aks` and `docker-desktop`. Check with `kubectl config current-context` before any command that changes something. Local-cluster commands in the docs always pass `--context docker-desktop`.
5. **Never use plain `az aks stop`.** Use `scripts/aks/pause.sh`, which backs up first. Its partner `scripts/aks/resume.sh` repairs the volumes and restores the data.

## Everyday flow: feature work into `dev`

1. Branch from `dev` (for example `fix/...`, `feat/...`, `docs/...`). Never commit to `dev` or `main` directly.
2. Open a pull request into `dev`. CI must pass: backend build and tests (`mvn verify`, including the Testcontainers integration tests), frontend lint, tests and build, Helm lint for all three profiles, CodeQL and Trivy.
3. Merge with **Squash and merge**. `dev` requires linear history (ruleset "Basic Rules").
4. Merging into `dev` deploys nothing.

## Releasing `dev` to `main`

A release is a pull request from `dev` into `main`. Pushing to `main` starts CD, but only when the release changes `backend/**`, `frontend/**`, `infra/helm/**` or the CD workflow itself. A docs-only release deploys nothing.

### 1. Check what is going out

```bash
git fetch origin
git log --oneline origin/main..origin/dev                                   # commits in the release
git diff --stat origin/main..origin/dev -- '*/db/migration/*'               # database migrations in it?
```

If any migration drops a table or column, or rewrites data, say so in the PR and treat step 3 as mandatory.

### 2. Make sure the cluster is running

CD fails if the cluster is paused.

```bash
az aks show -g rg-swiftdrop-prod -n swiftdrop-aks --query powerState.code -o tsv   # Running / Stopped
bash scripts/aks/resume.sh          # only if it says Stopped (see "Pause and resume")
```

### 3. Back up

```bash
az aks get-credentials -g rg-swiftdrop-prod -n swiftdrop-aks --overwrite-existing
bash scripts/db/backup-aks.sh       # → backups/<UTC timestamp>/, checksum-verified
```

Keep the printed folder name: it is your restore point for this release.

### 4. Open and merge the release PR

Open a PR with base `main` and compare `dev`. When CI is green, merge with **Create a merge commit**, never squash.

`main` (ruleset "Main rules") does not require linear history, so a merge commit keeps `dev` and `main` sharing history. A squash makes the next release PR conflict on every file, and `dev` then needs a "sync" PR (`git merge -s ours origin/main`) to recover. That happened four times before the rulesets were split on 19 Nov 2026.

### 5. Approve and watch the deploy

1. GitHub → **Actions → CD – AKS Deploy**. The run waits for approval on the `production` environment. Approve it.
2. Watch it: `gh run watch` (or follow the run in the browser). It builds only the components that changed, pushes them to ACR, then runs `helm upgrade --install --wait --timeout 20m`. Ten JVMs cold-starting on one node take 5–10 minutes.

### 6. Verify

```bash
kubectl get pods -n swiftdrop-apps            # every pod Running 1/1
kubectl get pods -n swiftdrop-data            # 9 × *-postgres-0 Running
kubectl get certificate -n swiftdrop-apps     # swiftdrop-tls READY=True
curl -I https://swiftdrop.bitcraft.biz
curl https://api.swiftdrop.bitcraft.biz/api/v1/auth/.well-known/jwks.json
```

Then sign in on https://swiftdrop.bitcraft.biz and open Swagger at https://api.swiftdrop.bitcraft.biz/swagger-ui.html.

### 7. If the deploy failed or the site misbehaves

The deploy does not roll back on its own (`--wait` without `--atomic`).

```bash
helm history swiftdrop -n default                       # find the last good revision
helm rollback swiftdrop <revision> -n default --wait
```

If the bad release ran a migration that damaged data, rolling back the pods is not enough. Restore the backup from step 3 as well (see "Backup and restore"), then roll back.

### Manual deploys

**Actions → CD – AKS Deploy → Run workflow** on `main` with `target_service`:

| Value | Use it when |
|---|---|
| `all` | First deploy, or ACR lost its images |
| `<component>` (e.g. `frontend`) | Rebuild one component |
| `none` | Only Helm values or secrets changed |
| blank | Same change detection as a push |

## Pause and resume (saving credit)

```bash
bash scripts/aks/pause.sh                       # back up all 9 databases, then stop the cluster
bash scripts/aks/resume.sh                      # start, repair volumes, restore the newest backup
bash scripts/aks/resume.sh backups/<stamp>      # ... restore a specific backup
bash scripts/aks/resume.sh --no-restore         # ... leave the databases empty (then reseed)
```

`pause.sh` refuses to run unless `kubectl` points at `swiftdrop-aks`, and it aborts without stopping anything if the backup fails. `resume.sh` asks you to type `restore` before it replaces any data. If the node survived the stop, it changes nothing and needs no restore.

Pause outside demo times; the student credit does not cover running 24/7 for long. Resume an hour before a demo, so there is time to fix anything that goes wrong.

## Backup and restore

```bash
bash scripts/db/backup-aks.sh                         # all 9 databases
bash scripts/db/backup-aks.sh iam order               # only some
bash scripts/db/restore-aks.sh backups/<stamp>        # all databases in that folder
bash scripts/db/restore-aks.sh backups/<stamp> iam    # only some
```

- Backups land in `backups/<UTC timestamp>/` on your laptop as compressed `pg_dump` files, with a SHA-256 manifest. Each dump is verified and retried up to 3 times.
- `backups/` is git-ignored because it holds real user data. Copy important backups somewhere safe; your laptop is the only copy.
- Restore all databases from the **same** folder. The services reference each other's ids, so mixing points in time leaves orphaned records.
- Restore is destructive. It asks you to type `restore`, scales each owning service to 0, replaces the database in one transaction (a failure leaves it unchanged), then scales the service back up.
- Backups are safe while the app is live; `pg_dump` reads a consistent snapshot.

## When something goes wrong

Start with two commands. They answer most questions:

```bash
az aks show -g rg-swiftdrop-prod -n swiftdrop-aks --query powerState.code -o tsv
kubectl get pods -A | grep -v Running
```

| What you see | Likely cause | What to do |
|---|---|---|
| Site does not load at all | Cluster is paused or stopped | `bash scripts/aks/resume.sh` |
| `az` says the subscription is disabled | Student credit ran out | Nothing can start until the subscription is renewed or upgraded. Your laptop backups are the only copy of the data |
| Pods `Pending` after a start, PVCs bound to an old node | AKS gave a new node; volumes still point at the old one | `bash scripts/aks/resume.sh`. It deletes the stale volumes, creates fresh ones and restores |
| Pods run but the data is empty | Node was replaced without a resume (upgrade, Azure repair) | Restore the newest backup with `restore-aks.sh`. With no backup, reseed (see below) |
| Service stuck in `Init:0/1` | Its database or RabbitMQ is not ready yet | `kubectl logs -n swiftdrop-apps <pod> -c wait-for-dependencies`, then check `swiftdrop-data` and `swiftdrop-messaging` |
| `CrashLoopBackOff` right after a release | Bad config or a failed migration | `kubectl logs -n swiftdrop-apps deploy/<service> --previous`, then roll back (release step 7) |
| Services log "password authentication failed" | `values-secrets.yaml` was regenerated after the databases were created | Put the original secrets back (`gh secret set HELM_SECRETS_VALUES --env production < values-secrets.yaml`, then deploy with `none`) |
| `ImagePullBackOff` | Image missing in ACR or ACR not attached | Deploy that component manually, or `az aks update -g rg-swiftdrop-prod -n swiftdrop-aks --attach-acr <acr>` |
| Browser shows a certificate error | cert-manager could not renew | `kubectl describe certificate -n swiftdrop-apps swiftdrop-tls`, then `kubectl describe challenge -A` |
| CD fails at "get credentials" or Helm cannot connect | Cluster paused during the release | Resume, then re-run the workflow |
| Azure portal shows no resources | Azure Resource Graph had not indexed them | Open the resource by its direct URL, or use `az` |
| A command meant for the local cluster ran against AKS | `kubectl` context was `swiftdrop-aks` | Stop. Check `helm history swiftdrop -n default` and the pods. If anything was deleted or uninstalled, re-deploy (`none`) and restore from the latest backup |

More symptoms are in the [deployment guide's troubleshooting table](AKS-HELM-DEPLOYMENT.md#12-troubleshooting).

## Reseeding demo data

If the data is gone and there is no backup, rebuild a month of realistic demo data through the public APIs. Follow [scripts/seed/README.md → A cluster that runs everything (AKS)](../../scripts/seed/README.md). It switches e-mail to mock while seeding so nobody receives real mail. Take a backup straight after it finishes.

The seed must run **on a working day morning (Sri Lanka time)**: dispatch checks the real clock, so shifts start only Monday to Saturday from 07:30 and pickups are given out only until 15:00. Check `/ops/working-days` that the day is not a holiday. It takes under an hour; `timeshift.mjs` then spreads the history over the past month.

### Starting over with fresh demo data

To replace old demo data (for example after the product rules changed), empty the cluster first:

```bash
kubectl config current-context        # must be swiftdrop-aks
bash scripts/db/wipe-aks.sh           # backs up all 9 databases, asks you to type "wipe", empties them
```

It deletes **everything**, including real people's accounts; the backup it prints is the only way back (`restore-aks.sh`). Services restart on empty databases, Flyway rebuilds the schemas, and auth-iam-service recreates the bootstrap admin. Then seed as above.

## Before a demo

1. Resume the cluster about an hour early (`resume.sh`).
2. Run the checks in release step 6 and sign in with one account per role ([demo accounts](DEMO-ACCOUNTS.md)).
3. Take a fresh backup.
4. Afterwards, pause (`pause.sh`).
