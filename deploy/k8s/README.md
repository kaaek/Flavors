# Kubernetes (Minikube) for the microservice stack

EEC-95, Phase 0 groundwork. `base/` holds the manifests, `minikube/` is the overlay for a local cluster. Nothing here
changes how the monolith is built or deployed today (Railway); this is the target platform.

## What is in the base

| Piece | Manifests |
|---|---|
| Namespace `flavors`, shared `ConfigMap`, `Secret` template (fake values only) | `namespace.yaml`, `configmap.yaml`, `secret.yaml` |
| 8 services (identity, creator, brand, campaign, proof, payment, offer, notification): Deployment + Service + its own Postgres StatefulSet, each | `<service>.yaml` |
| The monolith `backend` (default upstream) with its own Postgres, and `web` (the SPA behind nginx) | `backend.yaml`, `web.yaml` |
| RabbitMQ StatefulSet (management UI on 15672) | `rabbitmq.yaml` |
| Ingress with the path table of plan section 1.1 (same as `deploy/gateway/nginx.conf`) | `ingress.yaml` |

Every Java workload has startup, liveness (`/actuator/health/liveness`) and readiness (`/actuator/health/readiness`) probes.

**Placeholders.** The 8 service images do not exist yet. Their Deployments are annotated `flavors.io/placeholder: "true"`,
use image tags `placeholder-not-built` and have `replicas: 0`, so the base applies cleanly. Their Services point at the
monolith (`selector: app.kubernetes.io/name: backend`), so every route keeps working. **Strangler cutover for one service:**
build its image, set its Deployment's `replicas: 1`, and change its Service selector from `backend` to the service name.
`web` and `backend` need images too (step 3) but are real today.

## Start Minikube (about 12 GB RAM; the host has 32 GB)

Run everything from the repository root. The Docker runtime is required for `minikube docker-env`.

```bash
minikube start -p flavors --driver=docker --container-runtime=docker --cpus=4 --memory=12288 --disk-size=30g
minikube -p flavors addons enable ingress
```

## Build the images into Minikube's Docker

```bash
eval "$(minikube -p flavors docker-env)"          # this shell's `docker` now talks to Minikube
docker build -t flavors/backend:dev .             # the monolith (root Dockerfile)
docker build -f deploy/web/Dockerfile -t flavors/web:dev .
# once a service exists (Phase 1):  docker build -t flavors/identity:dev services/identity
```

## Apply the overlay

```bash
kubectl config use-context flavors
kubectl apply -k deploy/k8s/minikube
kubectl -n flavors get pods -w                    # databases and rabbitmq first, then backend and web
```

Without a cluster you can still render and lint it: `kubectl kustomize deploy/k8s/minikube` (CI also runs `kubeconform`).

## Reach the app

```bash
echo "$(minikube -p flavors ip) flavors.local" | sudo tee -a /etc/hosts
open http://flavors.local/                        # SPA via the Ingress; /api/** routes per the table
```

Fallbacks if the Ingress is not reachable (for example Docker Desktop on macOS, where you also need `minikube tunnel`):

```bash
kubectl -n flavors port-forward svc/web 8080:8080          # SPA only
kubectl -n flavors port-forward svc/backend 8081:8080      # monolith API
kubectl -n flavors port-forward svc/rabbitmq 15672:15672   # RabbitMQ UI (flavors / see secret.yaml)
```

## Configuration and secrets

`base/secret.yaml` contains `FAKE-...-change-me` values so the manifests apply. Replace the Secret for anything that is not
a throwaway laptop cluster, keeping the key names (`<service>-db-password`, `rabbitmq-*`, `jwt-signing-key`).

## Clean up

```bash
kubectl delete -k deploy/k8s/minikube       # keeps the volumes' data only until the PVCs are deleted:
kubectl -n flavors delete pvc --all
minikube -p flavors delete
```
