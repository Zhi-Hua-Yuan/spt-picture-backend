# 里程碑 0 回滚基线

建立日期：2026-10-11

本仓库当前基线提交为 `0b4e00c`（`master`）。进入里程碑 0 前，工作区已经包含 RabbitMQ、Elasticsearch、Flyway、异步任务和搜索降级相关的未提交改动；这些改动属于既有工作，不能用 `git checkout` 或 reset 覆盖。

当前执行环境对 `.git` 目录只读，无法写入 `index.lock`，因此不能在此处创建 Git checkpoint commit。工作区文件本身保持原样，`git diff` 与未跟踪文件清单仍可用于人工回滚；后续应在具备 Git 写权限的环境中，将本文件与当前工作区一起提交为 `chore: checkpoint before milestone 0`。

本文件只记录基线，不替代 Flyway 迁移、测试或应用配置。
