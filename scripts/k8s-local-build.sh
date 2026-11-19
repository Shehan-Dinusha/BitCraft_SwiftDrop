#!/usr/bin/env bash
# ──────────────────────────────────────────────────────────────────────────────
# Build SwiftDrop images for Docker Desktop's Kubernetes (values-local.yaml).
#
#   bash scripts/k8s-local-build.sh                        # core set + frontend
#   bash scripts/k8s-local-build.sh notification-service   # specific components
#   bash scripts/k8s-local-build.sh --docker api-gateway   # use the CD Dockerfiles
#
# Default (fast) mode compiles on this machine with Maven / npm, reusing the local
# ~/.m2 and node_modules caches, then wraps the result in a slim image
# (infra/local/k8s/Dockerfile.*). --docker instead runs the full CD Dockerfiles,
# which download every dependency inside Docker (slow, needs a stable network).
#
# Images are tagged "<component>:local" — the names values-local.yaml uses with
# imagePullPolicy: Never. Deployed components are restarted to pick up the image.
# ──────────────────────────────────────────────────────────────────────────────
set -euo pipefail
cd "$(dirname "${BASH_SOURCE[0]}")/.."

MODE=host
if [ "${1:-}" = "--docker" ]; then MODE=docker; shift; fi

CORE="api-gateway auth-iam-service customer-order-service pricing-zone-service frontend"
if [ "$#" -gt 0 ]; then components=("$@"); else read -ra components <<< "$CORE"; fi

backend=()
build_frontend=false
for c in "${components[@]}"; do
  if [ "$c" = "frontend" ]; then build_frontend=true
  elif [ -f "backend/$c/pom.xml" ]; then backend+=("$c")
  else echo "Unknown component: $c" >&2; exit 1
  fi
done

restart() {
  if kubectl get deployment "$1" -n swiftdrop-apps >/dev/null 2>&1; then
    kubectl rollout restart deployment "$1" -n swiftdrop-apps
  fi
}

if [ "$MODE" = "docker" ]; then
  for c in "${backend[@]}"; do
    echo "==> [docker] $c:local"
    docker build -t "$c:local" -f "backend/$c/Dockerfile" backend
    restart "$c"
  done
  if $build_frontend; then
    echo "==> [docker] frontend:local"
    docker build -t frontend:local -f frontend/Dockerfile frontend
    restart frontend
  fi
else
  if [ "${#backend[@]}" -gt 0 ]; then
    modules="$(IFS=,; echo "${backend[*]}")"
    echo "==> Maven package: $modules"
    mvn -B -q -f backend/pom.xml -pl "$modules" -am package -DskipTests --no-transfer-progress
    for c in "${backend[@]}"; do
      jar="$(ls backend/$c/target/*.jar 2>/dev/null | grep -v -- '-plain\.jar$' | head -1 || true)"
      [ -n "$jar" ] || { echo "No jar found in backend/$c/target" >&2; exit 1; }
      ctx="$(mktemp -d)"
      cp "$jar" "$ctx/app.jar"
      echo "==> Image $c:local  ($(basename "$jar"))"
      docker build -q -t "$c:local" -f infra/local/k8s/Dockerfile.service "$ctx" >/dev/null
      rm -rf "$ctx"
      restart "$c"
    done
  fi
  if $build_frontend; then
    echo "==> npm run build (frontend)"
    # Reinstall when package-lock.json changed since the last install (stale node_modules).
    if [ ! -f frontend/node_modules/.package-lock.json ] \
       || [ frontend/package-lock.json -nt frontend/node_modules/.package-lock.json ]; then
      echo "==> npm ci (package-lock.json changed)"
      (cd frontend && npm ci --no-audit --no-fund --loglevel=error)
    fi
    (cd frontend && npm run build --silent)
    echo "==> Image frontend:local"
    ctx="$(mktemp -d)"
    cp -r frontend/dist "$ctx/dist"
    docker build -q -t frontend:local -f infra/local/k8s/Dockerfile.frontend "$ctx" >/dev/null
    rm -rf "$ctx"
    restart frontend
  fi
fi

echo ""
docker images --filter "reference=*:local" --format "table {{.Repository}}:{{.Tag}}\t{{.Size}}\t{{.CreatedSince}}"
