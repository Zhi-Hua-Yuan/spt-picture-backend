# 里程碑 5 实施记录

本里程碑将“模块化单体 + 三个独立部署单元”推进到可交付闭环：统一 V1 消息协议、独立服务开关、重试/停车场/DLQ 运维路径、基础可观测性，以及 Helm/kind/GitHub Actions 交付链路。

## 已完成

- RabbitMQ 仅保留 V1 事件契约；旧路由、旧消费者、旧 OutboxWriter 和旧消息信封已移除。
- Core、Media、Search 的处理开关独立化，避免单一总开关导致服务间误启停。
- 发布端增加 confirm/return 检查；消费失败统一走 10s/60s/300s 重试，超过次数进入 DLQ；非法消息进入停车场，停车失败重新入队。
- DLQ 重放仅接受 V1 DLQ，并保留 eventId 重新投递，支持按目标队列恢复。
- Core 接入 Resilience4j 的 Search 远程调用熔断/重试；三服务接入 Prometheus 指标标签和 OpenTelemetry tracing bridge。
- 新增 Core/Media/Search 三组 ArchUnit 边界测试。
- 新增 Helm Chart、demo/prod values、PDB/HPA、ServiceMonitor/PrometheusRule 模板和 kind smoke 脚本。
- 后端 CI 同时检出前端 `master`，执行前端构建、Maven verify、Compose 校验、Helm lint/template 和 kind smoke；前端独立 CI 执行类型检查、构建和镜像构建。
- 前端镜像改用 `npm ci`、Node 22 和非 root Nginx 8080 端口，增加 SPA fallback 与 `/healthz`。

## 验证结果

- `mvn -B -ntp test`：Core 20 项、Media 5 项、Search 2 项，全部通过。
- `mvn -B -ntp -DskipTests package`：六个 Maven 模块全部打包成功。
- `docker compose -f docker-compose.milestone3.yml config -q`：通过。
- 前端 `npm run build`：类型检查和 Vite 构建通过；仅保留已有的大 chunk 提示。
- 完整 Maven 测试实际启动并验证了 MySQL、Redis、RabbitMQ、Elasticsearch Testcontainers。

## 已知交付边界

- 本机未安装 Helm，因此 Helm lint/template 由 CI 工作流执行；工作流已显式安装 Helm 3.16.4。
- `docker-compose.milestone4-dual-core.yml` 是叠加覆盖文件，必须与 `docker-compose.milestone3.yml` 一起使用。
- Helm demo values 假设 MySQL、Redis、RabbitMQ、Elasticsearch 由演示环境提供；若要做完全自包含 kind E2E，需要继续补充基础设施 Chart 或 kind 专用依赖清单。
