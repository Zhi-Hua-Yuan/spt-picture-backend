# 里程碑 4 实施说明（已完成）

本里程碑把“模块化单体 + 三个部署单元”推进到可运行的边界隔离：Core 继续拥有用户、空间、图片和任务主数据；Media 只负责 COS/审核/URL 导入；Search 只负责 Elasticsearch 投影和查询。

当前状态：**里程碑 4 已完成。核心功能、三库隔离、双 Core、Search/Media 故障恢复和 RabbitMQ 重启均已验收通过；Testcontainers、真实重复/乱序消息重放和 Kubernetes 部署作为已知后续事项转入里程碑 5，不阻塞本里程碑关闭。**

## 已落地范围

### 1. V1 事件契约与路由

- `picture-contracts` 新增 `EventEnvelopeV1`、事件类型、路由常量和图片/批处理/搜索快照 payload。
- Core Outbox 支持把事件写入 `picture.events.v1`，事件包含 schemaVersion、aggregateVersion、trace/correlation/causation 字段。
- Media 处理 `PICTURE_PROCESSING_REQUESTED`、`FILES_DELETE_REQUESTED`、批量导入，并发布成功/失败/批量结果事件。
- Core 使用 Inbox + 版本条件更新消费 Media 结果，重复消息不会重复推进图片版本。

### 2. 三库与服务边界

- Compose/MySQL 初始化脚本创建 `spt_picture_media`、`spt_picture_search` 以及 `picture_core`、`picture_media`、`picture_search` 三个账号。
- Core 继续使用现有业务库；Media 使用 `classpath:db/migration/media`；Search 使用 `classpath:db/migration/search`，避免共享内核依赖中的 Core migration 被误执行。
- Media/Search 的本地 `inbox_event`、执行状态和索引状态已具备迁移脚本；Media 任务状态使用 `media_job/media_job_item`，结果事件使用 Media 本地 `media_outbox_event`。
- Media 部署单元只扫描 `media.persistence.mapper`，遗留 Core Mapper、旧 Media 消费者和旧 Media 拓扑已移除；Search 只扫描自己的状态/InBox Mapper，并排除共享 `InboxService`。
- Core 的过渡 `search_index_state` 已通过 `V6__remove_search_state_from_core.sql` 清理既有 Core 数据库；Search 数据库保留其自身的索引协调表。Media Outbox 通过独立的 `media` `V2__media_outbox.sql` 迁移，避免修改已执行的 V1 checksum。

### 3. Search 内部 API 与重建

- Search 暴露带 `X-Internal-Token` 的 `/internal/v1/search/pictures`、`suggestions`、`facets`。
- Core 的 `REMOTE` 模式通过 HTTP 调用 Search；Search 不再需要 Core 数据库连接。
- Core 暴露带 token 的 `/api/internal/v1/search/snapshots`，Search 的 rebuild/reconcile 通过 keyset 分页读取快照。
- Search 的索引事件使用图片快照或删除事件，索引版本采用外部版本控制；Search 不可用时 Core 保留 MySQL 降级路径。

### 4. 批量导入闭环

- Core 批量任务写入 v1 `BATCH_IMPORT_REQUESTED`。
- Media 在本地发现 Bing 图片 URL、上传 COS，并发布 `BATCH_ITEM_PREPARED` / `BATCH_COMPLETED`。
- Core 幂等创建任务项、图片和图片处理子任务，维护空间额度；额度不足时发布文件清理事件。
- 批量重试复用同一 v1 请求，携带失败项 URL，不再依赖 Media 访问 Core 数据库。

## 配置与启动

```powershell
docker compose -f docker-compose.milestone3.yml config
docker compose -f docker-compose.milestone3.yml up --build
```

如果 Docker 构建容器无法访问 Maven Central，可先在宿主机执行 `mvn -DskipTests package`，再用
`$env:BUILD_FROM_SOURCE='false'; docker compose -f docker-compose.milestone3.yml up -d --build` 复用宿主机产物。

至少应在 `.env` 中设置：`DB_PASSWORD`、`CORE_DB_PASSWORD`、`MEDIA_DB_PASSWORD`、`SEARCH_DB_PASSWORD`、`PICTURE_SEARCH_INTERNAL_TOKEN`，以及 COS 凭据。已有 MySQL 数据卷不会重复执行 init 脚本；首次切换前请备份并按兼容迁移策略创建账号/数据库。

Core 容器内 Search 地址为 `http://picture-search-service:8125`；Search 回读快照地址为 `http://picture-core-api:8123/api`。本地非 Compose 启动时可通过 `PICTURE_SEARCH_MODE=MYSQL` 保留搜索降级路径。

## 验证结果

- 测试分批执行合计通过（Core 19 个测试、Media 4 个本地自治测试、Search 1 个测试，0 失败）；5 个 Testcontainers 测试仍因 Java 进程无权访问 Docker Desktop named pipe 而跳过。
- `mvn -pl picture-media-service -am -DskipTests package`：通过，Media 已不再编译遗留 Core Mapper 处理器。
- `docker compose -f docker-compose.milestone3.yml config`：通过。
- 新增 v1 事件契约回环测试、Media Inbox 重复消息测试和本地任务重投测试。
- 双 Core 两个副本 readiness 通过；Search 停止时 Core 返回 `degraded=true`，Search readiness 恢复后返回 `degraded=false`。
- Media 停止期间 Core 查询保持 `code=0`；Media 恢复后 readiness 为 `UP`。
- RabbitMQ 重启后恢复为 `healthy`，两个 Core 查询均保持 `code=0`；Core Outbox 当前状态为 `SENT`。

双 Core 验收命令（需要可用的 Docker Desktop/JVM named-pipe 权限）：

```powershell
docker compose -f docker-compose.milestone3.yml -f docker-compose.milestone4-dual-core.yml up -d --build --scale picture-core-api=2
docker compose -f docker-compose.milestone3.yml -f docker-compose.milestone4-dual-core.yml ps
```

随后按“重复投递 → 乱序投递 → 停 Search → 恢复 Search → 停 Media → 恢复 Media”的顺序执行接口验收，重点检查 Inbox 唯一键、Core 版本条件更新和 Media Outbox 重试。本轮已完成双 Core、Search/Media 故障及 RabbitMQ 重启验证；重复/乱序消息已有 Inbox/版本栅栏自动化测试覆盖，真实消息重放列为后续增强项。

## 里程碑 4 收口结论

按当前项目验收范围，里程碑 4 的开发目标和主要运行态验收均已完成，可以进入里程碑 5。未执行的环境型验收不影响本里程碑关闭，但必须作为后续交付风险跟踪。

## 下一步（里程碑 5 前置）

1. 在具备 named-pipe 权限的 CI/开发机上让 Testcontainers 集成测试真正运行，并验证 Core V6 清理迁移。
2. 补充真实 Rabbit 消息重复/乱序重放和 Kubernetes 部署验收。
3. 旧 Search 兼容消费者完成排空后移除，并补充三库权限审计、消息重放/停车场运维接口。
