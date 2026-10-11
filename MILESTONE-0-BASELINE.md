# 里程碑 0 回滚基线

建立日期：2026-10-11

本仓库当前基线提交为 `0b4e00c`（`master`）。进入里程碑 0 前，工作区已经包含 RabbitMQ、Elasticsearch、Flyway、异步任务和搜索降级相关的未提交改动；这些改动属于既有工作，不能用 `git checkout` 或 reset 覆盖。

已建立 Git checkpoint：`5be3243 chore: checkpoint before milestone 0`。该提交包含进入里程碑 0 前保留的异步化、搜索和 Flyway 改动，以及里程碑 0 的测试隔离与回归测试基础。

随后为兼容 Docker Desktop 4.56 / Engine 29，测试配置补充了 Docker API 1.44、Elasticsearch 7.17.29 和测试 profile 的 Knife4j 隔离；这些收尾修复在 checkpoint 之后单独提交。

本文件只记录基线，不替代 Flyway 迁移、测试或应用配置。
