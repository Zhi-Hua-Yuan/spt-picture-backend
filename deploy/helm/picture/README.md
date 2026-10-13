# Picture Helm deployment

This chart deploys the three backend services and the private frontend. The
Kind smoke test uses `values-demo.yaml`; a real cluster should use
`values-prod.yaml` together with a pre-created `picture-secrets` Secret.

## Prerequisites

- Kubernetes context pointing to the target cluster
- Helm 3.16 or newer
- NGINX Ingress Controller when `ingress.enabled=true`
- MySQL databases and users for the core, media, and search services
- Redis, RabbitMQ, and Elasticsearch endpoints reachable from the cluster
- The four images published by `backend-ci` to GHCR

The production values file intentionally sets `secrets.create=false`. Do not
put real credentials in a values file committed to Git. Create or update the
Secret out of band instead:

```bash
kubectl create namespace picture --dry-run=client -o yaml | kubectl apply -f -
kubectl -n picture create secret generic picture-secrets \
  --from-literal=CORE_DB_PASSWORD='replace-me' \
  --from-literal=MEDIA_DB_PASSWORD='replace-me' \
  --from-literal=SEARCH_DB_PASSWORD='replace-me' \
  --from-literal=COS_SECRET_ID='replace-me' \
  --from-literal=COS_SECRET_KEY='replace-me' \
  --from-literal=COS_REGION='ap-guangzhou' \
  --from-literal=COS_BUCKET_NAME='replace-me' \
  --from-literal=COS_HOST='https://replace-me' \
  --from-literal=PICTURE_SEARCH_INTERNAL_TOKEN='replace-me' \
  --dry-run=client -o yaml | kubectl apply -f -
```

If the GHCR packages are private, create the pull secret referenced by
`values-prod.yaml` using a GitHub token that has package read permission. Keep
this token outside Git:

```bash
kubectl -n picture create secret docker-registry ghcr-pull \
  --docker-server=ghcr.io \
  --docker-username='<github-user>' \
  --docker-password='<github-token-with-read-packages>' \
  --docker-email='<email>' \
  --dry-run=client -o yaml | kubectl apply -f -
```

## Deploy an immutable release

Use the Git SHA printed by the `Publish image summary` step. Overriding every
image tag keeps a rollout reproducible and avoids silently moving a running
release when the `latest` tag changes.

```bash
RELEASE_TAG='<git-sha>'

helm upgrade --install picture . \
  --namespace picture \
  --create-namespace \
  -f values-prod.yaml \
  --set images.core.tag="${RELEASE_TAG}" \
  --set images.media.tag="${RELEASE_TAG}" \
  --set images.search.tag="${RELEASE_TAG}" \
  --set images.frontend.tag="${RELEASE_TAG}" \
  --set ingress.host='pictures.example.com'
```

After the rollout, verify the release and the public endpoints:

```bash
helm status picture -n picture
kubectl rollout status deployment/picture-core -n picture
kubectl rollout status deployment/picture-media -n picture
kubectl rollout status deployment/picture-search -n picture
kubectl rollout status deployment/picture-frontend -n picture
kubectl get ingress -n picture
```

Before production traffic is enabled, verify login, image upload, COS
storage, space permissions, search, and WebSocket collaboration through the
Ingress hostname.
