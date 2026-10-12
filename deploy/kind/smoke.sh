#!/usr/bin/env bash
set -euo pipefail

CLUSTER_NAME="${KIND_CLUSTER_NAME:-picture-ci}"
NAMESPACE="${KIND_NAMESPACE:-picture-demo}"
ROOT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")/../.." && pwd)"

kind create cluster --name "${CLUSTER_NAME}" --wait 60s 2>/dev/null || true
trap 'kind delete cluster --name "${CLUSTER_NAME}" >/dev/null 2>&1 || true' EXIT

helm lint "${ROOT_DIR}/deploy/helm/picture" -f "${ROOT_DIR}/deploy/helm/picture/values-demo.yaml"
helm template picture "${ROOT_DIR}/deploy/helm/picture" \
  --namespace "${NAMESPACE}" \
  -f "${ROOT_DIR}/deploy/helm/picture/values-demo.yaml" >/tmp/picture-rendered.yaml

kubectl create namespace "${NAMESPACE}" --dry-run=client -o yaml | kubectl apply -f -
helm upgrade --install picture "${ROOT_DIR}/deploy/helm/picture" \
  --namespace "${NAMESPACE}" \
  -f "${ROOT_DIR}/deploy/helm/picture/values-demo.yaml" \
  --wait=false

# The smoke gate validates chart rendering, API object creation, and the static
# frontend pod. External data services are supplied by the demo environment.
kubectl rollout status deployment/picture-frontend -n "${NAMESPACE}" --timeout=180s
kubectl get deploy,svc -n "${NAMESPACE}"
