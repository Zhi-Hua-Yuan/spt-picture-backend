# Milestone 3 Implementation

Milestone 3 is implemented as a five-module Maven reactor and three independently deployable Spring Boot applications.

## Modules

| Module | Responsibility | Port |
| --- | --- | --- |
| `picture-contracts` | Versioned event envelope, event types, routing constants | — |
| `picture-shared-kernel` | MyBatis entities/mappers, Flyway resources, COS/Inbox/Outbox primitives | — |
| `picture-core-api` | Public REST/WebSocket APIs, authentication, task commands, outbox relay, search read/fallback | 8123 |
| `picture-media-service` | Process, batch, batch-item, and file-delete consumers | 8124 |
| `picture-search-service` | Search-index and index-maintenance consumers, rebuild/reconcile scheduler, active-alias ownership | 8125 |

Core keeps the existing public API and event JSON shape. Media and Search are idempotent consumers using the shared Inbox/Outbox tables. The first deployment still uses the shared MySQL schema; database separation remains a later milestone.

## Build and tests

```powershell
mvn -DskipTests package
mvn test
```

The integration tests use Testcontainers when a Docker engine is available. Without Docker they are skipped by the existing `disabledWithoutDocker` policy; unit and contract tests still run.

## Local three-service environment

```powershell
docker compose -f docker-compose.milestone3.yml up --build
```

This starts MySQL, Redis, RabbitMQ, Elasticsearch, Core API, Media, and Search. Core is the only service with Flyway enabled by default; Media and Search wait for the migrated shared schema.

Individual images can be built with:

```powershell
docker build --build-arg SERVICE=picture-core-api -f Dockerfile.service .
docker build --build-arg SERVICE=picture-media-service -f Dockerfile.service .
docker build --build-arg SERVICE=picture-search-service -f Dockerfile.service .
```

## Runtime switches

The `picture.async.*` switches are independent so a consumer can be paused without disabling the Core API or the outbox relay:

- `PICTURE_ASYNC_PROCESS_ENABLED`
- `PICTURE_ASYNC_BATCH_ENABLED`
- `PICTURE_ASYNC_FILE_DELETE_ENABLED`
- `PICTURE_ASYNC_SEARCH_INDEX_ENABLED`
- `PICTURE_ASYNC_INDEX_MAINTENANCE_ENABLED`
- `PICTURE_ASYNC_RECONCILE_ENABLED`

`PICTURE_ASYNC_ENABLED` is retained as a backwards-compatible Core API umbrella switch.

## COS upload configuration

The Core API performs the synchronous object-storage upload before publishing
the media-processing event. Set these variables in the Compose `.env` file or
the container environment before using file upload:

```dotenv
COS_SECRET_ID=your-tencent-secret-id
COS_SECRET_KEY=your-tencent-secret-key
COS_REGION=ap-guangzhou
COS_BUCKET_NAME=your-bucket-name-数字
COS_HOST=https://your-bucket-name-数字.cos.ap-guangzhou.myqcloud.com
```

Do not commit the real secret values. An empty COS configuration intentionally
leaves the service running for API/search development, but upload requests will
fail until the bucket configuration is supplied.
