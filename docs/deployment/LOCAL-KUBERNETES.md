# Running SwiftDrop on Kubernetes locally (free)

This runs SwiftDrop with the **same Helm chart** as AKS, on the Kubernetes built into Docker Desktop. It costs nothing, so it's the place to learn, test the chart and practise the demo. Use [AKS](AKS-HELM-DEPLOYMENT.md) only when you need a public URL.

It uses the [`values-local.yaml`](../../infra/helm/swiftdrop/values-local.yaml) profile:

| | Local | AKS (student) |
|---|---|---|
| Services running | **Core set:** gateway, auth, order, pricing, frontend | All 10 + frontend |
| Databases | 3 (only for the enabled services) | 9 |
| Broker and cache | RabbitMQ, Redis | RabbitMQ, Redis |
| Images | Built on your laptop (`<name>:local`), never pulled | From Azure Container Registry |
| URL | `http://swiftdrop.localhost` | `https://swiftdrop.bitcraft.biz` |
| RAM used (measured) | ~1.2 GB for the core set (each service ~200–250 MB, each database ~60 MB) | — |

All the Kubernetes objects the assignment asks for are there: Deployments, StatefulSets, Services, ConfigMaps, Secrets, Ingress and HPAs. The other six services are switched off only to save RAM.

## One-time setup

1. **Turn on Kubernetes.** In Docker Desktop, go to **Settings → Kubernetes → Enable Kubernetes**, choose cluster type **Kubeadm**, then click **Apply & restart**. Wait for *Kubernetes running*.
2. **Point kubectl at it:**
   ```bash
   kubectl config use-context docker-desktop
   kubectl get nodes          # docker-desktop   Ready
   ```
3. **Install the Ingress controller (Traefik):**
   ```bash
   helm repo add traefik https://traefik.github.io/charts
   helm repo update
   helm upgrade --install traefik traefik/traefik -n traefik --create-namespace \
     --set ingressClass.isDefaultClass=true \
     --set providers.kubernetesIngress.publishedService.enabled=true
   ```
   Docker Desktop exposes Traefik's LoadBalancer on `localhost:80`.
4. **Generate the secrets** (Git Bash). The e-mail is optional locally:
   ```bash
   bash scripts/generate-helm-secrets.sh
   ```
   This writes `infra/helm/swiftdrop/values-secrets.yaml`, which is git-ignored. Keep it: AKS can reuse the same file later.

## Build and deploy

```bash
# Build the core images with the same Dockerfiles CD uses (first run: ~15–30 min)
bash scripts/k8s-local-build.sh

# Deploy
helm upgrade --install swiftdrop infra/helm/swiftdrop -n default \
  -f infra/helm/swiftdrop/values-local.yaml \
  -f infra/helm/swiftdrop/values-secrets.yaml

# Watch everything start (Ctrl+C to stop watching)
kubectl get pods -A -l app.kubernetes.io/part-of=swiftdrop -w
```

The databases start first. Each service waits in `Init:0/1` until its database and RabbitMQ are ready, then takes 1–3 minutes to boot.

When every pod shows `1/1 Running`, open **http://swiftdrop.localhost**. Sign in as `admin`, with the password under `bootstrapAdmin` in `values-secrets.yaml`.

| What | URL |
|---|---|
| Web app | http://swiftdrop.localhost |
| Swagger UI | http://api.swiftdrop.localhost/swagger-ui.html |
| RabbitMQ UI | `kubectl port-forward -n swiftdrop-messaging svc/rabbitmq 15672:15672`, then http://localhost:15672 |

`*.localhost` addresses resolve to your own machine in Chrome, Edge, Firefox and curl, so no hosts-file edit is needed.

## Everyday commands

| Task | Command |
|---|---|
| See everything | `kubectl get pods,svc,ingress,hpa -n swiftdrop-apps` |
| Logs of a service | `kubectl logs -n swiftdrop-apps deploy/api-gateway -f` |
| Inspect config | `kubectl get configmap api-gateway-config -n swiftdrop-apps -o yaml` |
| Rebuild one service after a code change | `bash scripts/k8s-local-build.sh customer-order-service` (restarts its pod automatically) |
| Turn on another service | Set `enabled: true` for it in `values-local.yaml`, build its image, run `helm upgrade` again. Its database is created automatically |
| Pause (free the RAM) | `helm uninstall swiftdrop -n default`. The database volumes are kept |
| Delete everything, including data | `helm uninstall swiftdrop -n default`, then `kubectl delete namespace swiftdrop-apps swiftdrop-data swiftdrop-messaging` |

## Troubleshooting

| Symptom | Fix |
|---|---|
| `ErrImageNeverPull` | The image isn't built, or Kubernetes can't see Docker's images. Run the build script, and use the **Kubeadm** cluster type, not kind |
| Pod `OOMKilled`, or the laptop crawls | Not enough RAM. Close other apps, or turn off `pricing-zone-service` in `values-local.yaml` |
| `Pending` pods: "Insufficient memory" | Same as above: Docker Desktop has about 6 GB to share |
| http://swiftdrop.localhost doesn't load | `kubectl get svc -n traefik`. The EXTERNAL-IP should be `localhost`. Something else (IIS, Skype) might be using port 80 |
| Service stuck in `Init:0/1` | `kubectl logs -n swiftdrop-apps <pod> -c wait-for-dependencies`, then check `kubectl get pods -n swiftdrop-data` |
