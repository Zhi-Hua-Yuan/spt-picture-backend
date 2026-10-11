# SPT Picture Backend

一个基于 Spring Boot 3.5 + MyBatis-Plus 的图片管理与协作图库后端项目。

> 当前文档对应里程碑 1：完成 Spring Boot 3.5、Jakarta、Springdoc OpenAPI 3、Sa-Token 会话兼容和单体运行基线。用户会话命名空间已升级，发布后需要重新登录。

这个项目围绕“用户、图片、空间、成员权限”四个核心对象展开，支持公共图库、私有空间、团队空间、图片审核、对象存储上传、颜色检索、空间分析、WebSocket 协同编辑以及 AI 扩图等能力。

## 项目特性

- 用户注册、登录、注销、当前登录态查询
- 公共图库浏览与图片详情查询
- 图片上传，支持本地文件上传和 URL 上传
- 图片编辑、删除、批量编辑、批量抓取上传
- 图片审核流转，支持管理员审核和对象存储内容审核
- 私有空间、团队空间管理
- 团队成员管理与角色权限控制
- 按主色调检索相似图片
- 空间使用分析、分类分析、标签分析、体积分布分析、用户上传行为分析、空间排行
- 团队空间下的图片协同编辑 WebSocket 通道
- 对接腾讯云 COS 做图片存储、压缩和缩略图生成
- 对接阿里云 DashScope 做 AI 扩图任务创建与结果查询

## 技术栈

| 类别 | 说明 |
| --- | --- |
| 语言 | Java 17 |
| 框架 | Spring Boot 3.5.16 |
| ORM | MyBatis-Plus 3.5.17 |
| 数据库 | MySQL |
| 缓存 / 会话 | Redis、Spring Session |
| 权限 | 自定义 `@AuthCheck` + Sa-Token |
| 文档 | Springdoc OpenAPI 3 / Swagger UI |
| 存储 | 腾讯云 COS |
| AI 能力 | 阿里云 DashScope |
| 实时通信 | Spring WebSocket |
| 异步消息 | RabbitMQ、LMAX Disruptor |
| 工具库 | Hutool、Jsoup |
| 数据访问 | 单体应用共享 MySQL，暂不引入分库分表代理 |

## 核心业务模型

### 1. 用户 `user`

系统用户，区分普通用户与管理员。

### 2. 图片 `picture`

图片主体信息，包括：

- 图片地址、缩略图地址
- 名称、简介、分类、标签
- 尺寸、宽高、格式、主色调
- 所属用户、所属空间
- 审核状态、审核人、审核时间

### 3. 空间 `space`

图片的组织容器，支持两种类型：

- 私有空间：仅空间所有者或管理员可完全操作
- 团队空间：通过 `space_user` 表维护成员和角色

空间等级由代码内枚举控制：

| 等级 | 值 | 最大图片数 | 最大总容量 |
| --- | --- | --- | --- |
| 普通版 | `0` | 100 | 100 MB |
| 专业版 | `1` | 1000 | 1000 MB |
| 旗舰版 | `2` | 10000 | 10000 MB |

### 4. 空间成员 `space_user`

用于描述团队空间里的成员角色。

| 角色 | 值 | 权限 |
| --- | --- | --- |
| 浏览者 | `viewer` | `picture:view` |
| 编辑者 | `editor` | `picture:view`、`picture:upload`、`picture:edit`、`picture:delete` |
| 管理员 | `admin` | `spaceUser:manage`、`picture:view`、`picture:upload`、`picture:edit`、`picture:delete` |

## 目录结构

```text
spt-picture-backend
├─ sql
│  └─ create_table.sql            # 数据库初始化脚本
├─ src/main/java/com/spt/sptpicturebackend
│  ├─ controller                  # 接口层
│  ├─ service / impl              # 业务层
│  ├─ mapper                      # MyBatis-Plus Mapper
│  ├─ model                       # DTO / VO / Entity / Enum
│  ├─ manager                     # COS、上传模板、鉴权、WebSocket、Disruptor 等
│  ├─ config                      # Spring 配置
│  ├─ exception                   # 统一异常处理
│  └─ common                      # 通用响应、分页、删除请求等
├─ src/main/resources
│  ├─ application.yml             # 主配置文件
│  ├─ biz/spaceUserAuthConfig.json
│  └─ mapper/*.xml
├─ src/test                       # 测试代码
├─ Dockerfile
└─ pom.xml
```

## 核心功能说明

### 图片上传与存储

- 支持 `MultipartFile` 上传和图片 URL 上传
- 上传入口统一走模板方法 `PictureUploadTemplate`
- 存储路径按业务自动区分：
  - 公共图库：`public/{userId}`
  - 空间图片：`space/{spaceId}`
- 上传到 COS 时会同时做：
  - 原图信息提取
  - WebP 压缩产物生成
  - 缩略图生成
  - 主色调提取

### 图片审核

- 普通用户上传或编辑图片后，默认进入“待审核”
- 管理员上传或编辑图片时，会调用腾讯云 COS 图片审核能力补充审核结论
- 审核状态枚举：

| 状态 | 值 |
| --- | --- |
| 待审核 | `0` |
| 通过 | `1` |
| 拒绝 | `2` |

### 空间与协作

- 支持私有空间与团队空间
- 非管理员用户只能创建普通等级空间
- 非管理员用户对同一种空间类型最多只能创建一个空间
- 团队空间通过 `space_user` 管理成员与角色

### 实时协同编辑

- WebSocket 地址：`ws://localhost:8123/api/ws/picture/edit?pictureId={pictureId}`
- 仅团队空间图片支持协同编辑
- 仅拥有 `picture:edit` 权限的成员可建立连接
- 支持的消息类型：
  - `INFO`
  - `ERROR`
  - `ENTER_EDIT`
  - `EXIT_EDIT`
  - `EDIT_ACTION`
- 当前定义的编辑动作：
  - `ZOOM_IN`
  - `ZOOM_OUT`
  - `ROTATE_LEFT`
  - `ROTATE_RIGHT`
- 后端通过 Disruptor 处理编辑消息，降低高并发下的消息处理压力

### 空间数据分析

系统提供以下分析能力：

- 空间容量与数量使用情况
- 图片分类分布
- 图片标签分布
- 图片大小区间分布
- 用户上传行为趋势
- 空间容量排行榜

## 接口概览

项目统一上下文路径为：

```text
/api
```

启动成功后：

- 健康检查：`GET /api/health`
- 接口文档：`http://localhost:8123/api/doc.html`
- OpenAPI JSON：`http://localhost:8123/api/v3/api-docs`

主要接口模块如下：

| 模块 | 路径前缀 | 说明 |
| --- | --- | --- |
| 用户模块 | `/user` | 注册、登录、注销、查询当前用户、管理员管理用户 |
| 图片模块 | `/picture` | 上传、列表、详情、编辑、删除、审核、批量上传、颜色检索、AI 扩图 |
| 空间模块 | `/space` | 新增、编辑、删除、查询空间、空间等级列表 |
| 空间成员模块 | `/spaceUser` | 新增成员、移除成员、查询成员、查询我加入的团队空间 |
| 空间分析模块 | `/space/analyze` | 使用情况、分类、标签、体积、用户趋势、排行分析 |
| WebSocket | `/ws/picture/edit` | 团队空间图片协同编辑 |

## 统一响应格式

接口统一返回 `BaseResponse<T>`：

```json
{
  "code": 0,
  "data": {},
  "message": ""
}
```

补充说明：

- 全局异常由 `GlobalExceptionHandle` 统一处理
- `Long` 类型会被序列化为字符串，避免前端精度丢失

## 环境依赖

本地启动前至少准备以下环境：

- JDK 17
- Maven 3.8+
- MySQL 8.x
- Redis 6.x 或更高版本
- RabbitMQ 3.13（可通过 `docker compose -f docker-compose.infra.yml up -d rabbitmq` 启动）
- 腾讯云 COS 账号与 Bucket
- 阿里云 DashScope API Key（如果要使用 AI 扩图）

## 配置说明

主配置文件位于：

```text
src/main/resources/application.yml
```

仓库中已经提供了本地开发配置骨架，但在实际运行前建议替换为你自己的连接信息与密钥，尤其是数据库、Redis、COS 和 AI 配置。

建议重点检查以下配置项：

```yaml
server:
  port: 8123
  servlet:
    context-path: /api

spring:
  datasource:
    url: jdbc:mysql://localhost:3306/spt_picture
    username: your_username
    password: your_password
    driver-class-name: com.mysql.cj.jdbc.Driver
  data:
    redis:
      host: 127.0.0.1
      port: 6379
      database: 0
sa-token:
  token-name: satoken-space
  timeout: 2592000

cos:
  client:
    secretId: your_secret_id
    secretKey: your_secret_key
    bucketName: your_bucket_name
    region: your_region
    host: https://your-cos-host

aliYunAi:
  apiKey: your_dashscope_api_key
```

### 上传限制

代码里实际生效的业务限制如下：

- 单张图片大小不超过 `3MB`
- 支持格式：`jpg`、`jpeg`、`png`、`webp`
- Spring MVC 的 multipart 上限配置为 `10MB`，但上传模板会进一步限制到 `3MB`

## 数据库初始化

数据库初始化与演进只使用 `src/main/resources/db/migration/V1__baseline.sql` 至
`V5__milestone2_correctness.sql`，由 Flyway 在应用启动时执行。不要再手工执行 SQL 或让
`sql/create_table.sql` 与 Flyway 并行维护；已有数据库应在发布前由部署流程完成
Flyway 基线确认。执行 V5 前，请先按 [MILESTONE-2-IMPLEMENTATION.md](MILESTONE-2-IMPLEMENTATION.md)
中的 SQL 检查空间、成员和批量任务图片的重复数据。

## 本地启动

### 异步图片处理

阶段 2 默认开启 RabbitMQ 异步处理。普通上传会立即返回图片和 `processTaskId`，审核、批量导入和 COS 文件清理由后台任务完成。

任务接口：

- `POST /api/picture/batch/tasks`
- `GET /api/picture/tasks/{taskId}`
- `POST /api/picture/tasks/{taskId}/retry`

如果本地暂时没有 RabbitMQ，可将 `PICTURE_ASYNC_ENABLED=false`，上传会回退到原有同步行为；生产环境应先启动 RabbitMQ，再开启异步开关。

### 1. 修改配置

根据你的环境调整 `application.yml` 中的：

- MySQL 连接
- Redis 连接
- COS 配置
- `aliYunAi.apiKey`

### 2. 启动 Redis

确保 Redis 可用，否则 Sa-Token 登录态、空间权限会话、跨实例协作锁、WebSocket Pub/Sub
和消息 Inbox 租约都无法正常使用。

### 3. 启动项目

开发模式：

```bash
mvn spring-boot:run
```

打包运行：

```bash
mvn clean package
java -jar target/spt-picture-backend-0.0.1-SNAPSHOT.jar
```

### 4. 验证启动

可以通过以下地址确认服务是否可用：

```text
http://localhost:8123/api/health
http://localhost:8123/api/doc.html
```

## Docker 部署

项目已提供 `Dockerfile`，默认流程如下：

```bash
mvn clean package
docker build -t spt-picture-backend .
docker run -p 8123:8123 spt-picture-backend
```

说明：

- Dockerfile 会复制 `target/spt-picture-backend-0.0.1-SNAPSHOT.jar`
- 容器启动命令默认使用 `--spring.profiles.active=prod`
- 生产环境配置位于 `src/main/resources/application-prod.yml`，敏感项通过环境变量注入

## 测试

当前仓库包含的测试主要有：

- Spring Boot 上下文加载测试
- Redis 字符串操作测试
- MD5 加密测试
- URL 校验示例测试

运行测试（需要本机 Docker，用 Testcontainers 自动启动 MySQL 8、Redis、RabbitMQ 3.13
和 Elasticsearch 7.17；不会连接开发机上的 MySQL/Redis）：

```bash
mvn test
```

## 当前代码里的注意事项

### 1. 当前采用单体共享数据库

里程碑 1 移除了 ShardingSphere 运行时依赖和分表预留代码，应用直接使用物理 MySQL 数据源。后续如有明确容量和隔离需求，再按领域边界设计独立部署单元和数据迁移方案。

### 2. 会话依赖 Redis

用户登录态统一存储在 Sa-Token Redis 中，因此 Redis 不可用时，登录、空间权限和协作编辑相关流程都会受影响。

### 3. 图片公开逻辑依赖 `spaceId`

- `spaceId = null` 代表公共图库图片
- 有 `spaceId` 代表属于某个空间

公共图库列表默认只展示审核通过的数据。

## 后续可扩展方向

- 增加更完整的业务集成测试与接口测试
- 将敏感配置迁移到环境变量或配置中心
- 按容量和领域边界评估独立部署单元与数据库拆分
- 为协同编辑补充更丰富的操作类型与冲突控制策略
- 增加对象存储、审核、AI 服务的降级与重试机制

---

如果你要继续完善这个项目，比较推荐下一步优先做两件事：

1. 把配置拆分为 `local` / `prod` 两套环境
2. 补齐核心业务链路的接口测试与权限测试
