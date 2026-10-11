# 里程碑 2：正确性与可靠性落地

里程碑 2 已在当前代码库完成第一版实现，目标是让三个独立部署单元可以安全扩容，并把跨实例协作、消息处理和删除语义从 JVM 内存约束提升到 Redis、数据库和 RabbitMQ 约束。

## 已落地能力

- **数据库约束**：`V5__milestone2_correctness.sql` 为有效空间、有效成员、批量任务图片和 Inbox 增加数据库级唯一性；Outbox 增加目标交换机字段。
- **统一会话**：登录态统一使用 Sa-Token Redis，不再把用户对象写入 HttpSession；用户禁用、空间删除会检查有效资源并逻辑删除关联数据。
- **空间权限**：成员管理接口增加空间成员管理权限注解；空间创建使用数据库唯一索引和事务锁，移除 JVM `synchronized`。
- **协作编辑**：WebSocket 会话只保留本 Pod 的连接，编辑锁使用 Redis Lua 原子抢占、续租和释放，事件通过 Redis Pub/Sub 广播；前端使用同源 WebSocket 和指数退避重连。
- **消息幂等**：Inbox 从“消费后插入日志”升级为带租约的 `NEW/PROCESSING/SUCCEEDED/FAILED` 状态机；同一事件只有成功状态会永久去重，异常状态可重新领取。
- **可靠投递**：业务消息统一走 Outbox；消费者发布重试和停车消息时检查 publisher confirm 与 returned message；未知/非法消息进入 parking queue，最终失败事件写入 DLQ Outbox。
- **搜索一致性**：移除图片服务中的 DIRECT/DUAL 直连同步路径，图片变化统一通过 Outbox → RabbitMQ → 搜索消费者；搜索不可用时保留现有降级查询能力。
- **批量导入幂等**：批量项使用稳定的 `taskId/taskItemId/eventId` 对象键，并由 `picture.taskItemId` 唯一索引和重复上传回读保护共同兜底。

## 发布前检查

1. 先在预发布数据库执行重复数据检查，确认以下查询均无结果，再运行 Flyway V5：

   ```sql
   SELECT userId, spaceType, COUNT(*) c FROM space WHERE isDelete = 0 GROUP BY userId, spaceType HAVING c > 1;
   SELECT spaceId, userId, COUNT(*) c FROM space_user GROUP BY spaceId, userId HAVING c > 1;
   SELECT taskItemId, COUNT(*) c FROM picture WHERE taskItemId IS NOT NULL GROUP BY taskItemId HAVING c > 1;
   ```

2. 所有应用实例必须配置同一个 Redis、RabbitMQ 和 MySQL；Redis 用于 Sa-Token、协作锁、Pub/Sub 和 Inbox 续租。
3. 确认 RabbitMQ 用户具备创建 exchange、queue、binding 的权限，并观察 `picture.parking.q`、各 DLQ 和 Outbox 未发送数量。
4. 多实例灰度期间先验证：同一图片只能有一个编辑 owner、重复消息不会重复创建图片、空间删除会拒绝仍有有效图片的空间。

## 关键配置

```yaml
sa-token:
  token-name: satoken-space
picture:
  websocket:
    lock-ttl-ms: 30000
    lock-renew-ms: 10000
  inbox:
    lease-seconds: 60
```

`PICTURE_WS_LOCK_TTL_MS`、`PICTURE_WS_LOCK_RENEW_MS`、`PICTURE_WS_CHANNEL` 和 `PICTURE_INBOX_LEASE_SECONDS` 可通过环境变量覆盖。

## 验证结果

- `mvn -DskipTests compile`：通过。
- `mvn test`：17 个测试通过，5 个依赖 Docker/Testcontainers 的集成测试因当前环境 Docker pipe 不可用而跳过。
- 前端 `npx vue-tsc --noEmit --pretty false --incremental false`：通过。
- 前端 `npm run build`：通过（Vite 生产构建有既有的大 chunk 体积提示）。

