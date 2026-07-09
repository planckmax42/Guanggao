# 广告投放与实时数据分析平台开发文档

## 1. 项目目标

本项目面向 Java 后端简历项目，目标是实现一个具备真实后端系统特征的「广告投放与实时数据分析平台」。

项目不只做后台 CRUD，而是覆盖广告系统的核心链路：

- 广告主、广告计划、广告素材、定向规则管理
- 广告召回、过滤、排序和投放
- 曝光、点击、转化事件采集
- Kafka 异步解耦
- Redis 实时计数、点击去重、频控、限流
- MySQL 持久化业务数据和统计数据
- Elasticsearch 支持广告素材搜索和事件检索
- 后台数据看板
- 后期 Docker Compose 一键部署

## 2. 技术栈约定

后端技术：

- Java 17
- Spring Boot 3.x
- Spring MVC
- MyBatis-Plus
- MySQL 8.x
- Redis 7.x
- Kafka 3.x
- Elasticsearch 8.x
- Maven

部署技术：

- Linux
- Docker
- Docker Compose

开发工具建议：

- IntelliJ IDEA
- Postman / Apifox
- Navicat / DataGrip
- RedisInsight / redis-cli
- Kibana / Elasticsearch Head

## 3. 开发环境策略

### 3.1 第一阶段：本机服务开发

项目初期优先使用本机已安装的中间件，不急于 Docker 化。

本机服务包括：

- 本机 MySQL
- 本机 Redis
- 本机 Kafka
- 本机 Elasticsearch

原因：

- 便于快速调试代码
- 便于直接查看数据库、缓存、消息和索引内容
- 降低一开始环境编排复杂度
- 先把业务链路跑通，再做容器化

本地默认连接配置建议：

```yaml
spring:
  datasource:
    url: jdbc:mysql://localhost:3306/ad_platform?useUnicode=true&characterEncoding=utf-8&serverTimezone=Asia/Shanghai
    username: root
    password: root
  data:
    redis:
      host: localhost
      port: 6379
  kafka:
    bootstrap-servers: localhost:9092

elasticsearch:
  uris: http://localhost:9200
```

实际密码以本机配置为准，不能把个人真实密码提交到 Git。

当前本机初始化 SQL 执行顺序：

```text
00-create-database.sql
01-admin-schema.sql
02-delivery-schema.sql
04-tracking-report-schema.sql
99-seed-demo-data.sql
```

### 3.2 第二阶段：Docker Compose 集成

当核心功能完成后，再加入 Docker Compose。

容器化目标：

- MySQL 容器
- Redis 容器
- Kafka 容器
- Elasticsearch 容器
- Spring Boot 应用容器

Docker 化后要支持：

- 一条命令启动完整项目
- 自动初始化数据库结构
- 应用服务读取容器网络内的服务地址
- README 中提供部署和访问说明

## 4. 项目目录规范

初期使用单体 Spring Boot 工程，按业务域拆包。

```text
ad-platform
├── pom.xml
├── README.md
├── DEVELOPMENT.md
├── docker-compose.yml
├── src/main/java/com/example/adplatform
│   ├── AdPlatformApplication.java
│   ├── common
│   │   ├── response
│   │   ├── exception
│   │   ├── enums
│   │   ├── constant
│   │   └── util
│   ├── admin
│   │   ├── controller
│   │   ├── service
│   │   ├── service/impl
│   │   ├── mapper
│   │   ├── entity
│   │   ├── dto
│   │   └── vo
│   ├── delivery
│   │   ├── controller
│   │   ├── service
│   │   ├── strategy
│   │   ├── dto
│   │   └── vo
│   ├── tracking
│   │   ├── controller
│   │   ├── producer
│   │   ├── dto
│   │   └── event
│   ├── consumer
│   │   ├── listener
│   │   └── service
│   ├── report
│   │   ├── controller
│   │   ├── service
│   │   ├── dto
│   │   └── vo
│   └── infra
│       ├── redis
│       ├── kafka
│       ├── elasticsearch
│       └── ratelimit
└── src/main/resources
    ├── application.yml
    ├── application-local.yml
    ├── application-docker.yml
    ├── mapper
    └── sql
```

后期如果项目复杂度提高，可以拆成 Maven 多模块，但第一版不建议过早拆分。

## 5. 分阶段开发计划

### 阶段 0：项目初始化

目标：

- 创建 Spring Boot 项目
- 引入基础依赖
- 配置 MyBatis-Plus
- 配置本机 MySQL、Redis
- 建立统一响应体和异常处理

任务：

- 创建 `pom.xml`
- 创建 `application.yml` 和 `application-local.yml`
- 创建数据库 `ad_platform`
- 编写基础 SQL 文件
- 实现 `Result<T>` 统一返回结构
- 实现 `GlobalExceptionHandler`
- 实现基础枚举和错误码
- 提供健康检查接口

验收标准：

- 应用可以本机启动
- `/api/health` 返回正常
- MySQL 可以连接
- Redis 可以连接
- 全局异常返回格式统一

### 阶段 1：后台基础管理模块

目标：

- 完成广告系统基础数据管理
- 先把广告主、广告计划、素材、定向规则数据打通

任务：

- 广告主管理
- 广告计划管理
- 广告素材管理
- 广告定向规则管理
- 广告计划上线、暂停、下线
- 广告素材审核通过、审核拒绝

核心表：

- `advertiser`
- `ad_campaign`
- `ad_creative`
- `ad_targeting_rule`
- `ad_slot`

接口：

- `POST /api/admin/advertisers`
- `GET /api/admin/advertisers/page`
- `POST /api/admin/campaigns`
- `PUT /api/admin/campaigns/{id}`
- `PUT /api/admin/campaigns/{id}/online`
- `PUT /api/admin/campaigns/{id}/pause`
- `POST /api/admin/creatives`
- `PUT /api/admin/creatives/{id}/audit`
- `POST /api/admin/targeting-rules`

验收标准：

- 能通过接口创建一个完整可投放广告
- 广告计划状态流转正确
- 素材审核状态正确
- 分页接口可用
- 关键字段有参数校验

### 阶段 2：广告投放模块

目标：

- 实现广告请求、召回、过滤、排序和返回
- 体现广告系统核心业务能力

任务：

- 定义广告请求 DTO
- 根据广告位召回候选广告
- 过滤广告状态
- 过滤投放时间
- 过滤预算不足广告
- 过滤定向规则不匹配广告
- 过滤用户频控超限广告
- 实现广告排序策略
- 返回 Top N 广告

排序策略第一版：

```text
score = bidPrice * 0.7 + ctrScore * 0.2 + qualityScore * 0.1
```

第一版可以没有真实 CTR，先使用默认 CTR 或 Redis 统计结果计算。

接口：

- `POST /api/delivery/ads`

验收标准：

- 不在线广告不会返回
- 不符合地域、设备、人群标签的广告不会返回
- 超出投放时间的广告不会返回
- 排序结果稳定且可解释
- 返回结果包含 `requestId`

### 阶段 3：MySQL 版业务闭环

目标：

- 先不依赖 Kafka、Elasticsearch，把广告业务主流程跑完整
- 实现曝光、点击、转化事件采集
- 实现 CPC、CPM、CPA 三种计费方式
- 实现事件去重、预算扣减和基础统计看板
- 投放链路的预算过滤、CTR 读取和用户频控先基于 MySQL 事件/统计表实现

任务：

- 给广告计划增加 `billing_type`
- 新建 `ad_event` 原始事件表
- 新建 `ad_stats_daily` 日统计表
- 实现统一事件上报接口
- 根据 `eventId` 做幂等去重
- 根据计费方式计算费用
- 根据总预算和日预算判断是否允许扣费
- 同步累加曝光、点击、转化和消耗
- 提供日统计、漏斗统计和素材排行查询接口
- 投放接口通过 `ad_stats_daily` 判断预算和 CTR
- 投放接口通过 `ad_event` 中的真实曝光事件判断用户频控

计费规则第一版：

```text
CPC：点击事件扣 bid_price
CPM：每累计 1000 次曝光扣 bid_price
CPA：转化事件扣 bid_price
```

接口：

- `POST /api/tracking/events`
- `GET /api/report/daily`
- `GET /api/report/funnel`
- `GET /api/report/top-creatives`

验收标准：

- 重复 `eventId` 不会重复落库和重复统计
- CPC 点击能产生费用
- CPC 曝光只统计不扣费
- 同一用户同一计划当天真实曝光达到频控阈值后，投放接口不再返回该计划
- 广告计划达到日预算或总预算后，投放接口不再返回该计划
- 日统计能查到曝光、点击、转化、消耗、CTR、CVR
- 漏斗接口能按时间范围汇总数据
- 素材排行接口能按消耗、点击、曝光排序

### 阶段 4：Redis 缓存与实时控制

目标：

- 在业务闭环已经跑通的基础上，引入 Redis 解决实时计数、频控、预算缓存和限流问题
- Redis 阶段是对阶段 3 MySQL 实现的性能增强，不改变业务口径

任务：

- 广告候选列表缓存
- 广告实时曝光数、点击数、转化数统计
- 用户广告频控
- 点击去重
- 广告日预算消耗缓存
- Redis Lua 限流脚本
- 用 Redis 替换投放链路中的高频预算、CTR、频控读取

核心 Key：

```text
ad:delivery:candidates:{slotCode}
ad:stats:rt:{yyyyMMdd}:{campaignId}
ad:creative:stats:rt:{yyyyMMdd}:{creativeId}
ad:freq:user:{userId}:{campaignId}:{yyyyMMdd}
ad:dedup:click:{userId}:{creativeId}:{yyyyMMdd}
ad:budget:daily:{yyyyMMdd}:{campaignId}
rate:api:{apiName}:{ip}:{timestampSecond}
```

验收标准：

- Redis 中能看到实时统计数据
- 同一用户重复点击可以被识别
- 同一用户广告展示次数可限制
- 高频请求会触发接口限流
- Redis 操作失败时有合理异常处理

### 阶段 5：Kafka 异步事件模块

目标：

- 曝光、点击、转化事件不直接在接口线程里完成所有重写数据库逻辑
- 通过 Kafka 完成异步解耦和削峰填谷

任务：

- 复用阶段 3 的统一广告事件模型
- 事件接口写入 Kafka
- Kafka Consumer 异步消费
- 消费后更新 Redis 统计
- 消费后写入 MySQL 事件明细和日统计
- 保留 `eventId` 幂等能力

Topic：

```text
ad-event
```

验收标准：

- 事件接口快速返回
- Kafka 能收到消息
- Consumer 能正常消费
- `ad_event` 表有事件记录
- Redis 实时统计同步增加
- 重复 eventId 不会重复落库

### 阶段 6：统计看板增强模块

目标：

- 提供更完整的广告效果数据查询能力
- 展示实时数据、日维度数据、漏斗数据和 Top 素材

任务：

- 实时统计接口
- 日统计接口
- CTR、CVR 计算
- Top 广告素材接口
- 曝光点击转化漏斗接口
- 定时任务将 Redis 实时数据聚合到 MySQL

接口：

- `GET /api/report/realtime`
- `GET /api/report/daily`
- `GET /api/report/top-creatives`
- `GET /api/report/funnel`

验收标准：

- 可查询单个广告计划实时数据
- 可查询一段时间内的趋势数据
- CTR、CVR 计算正确
- Redis 和 MySQL 统计口径说明清楚

### 阶段 7：Elasticsearch 检索模块

目标：

- 实现广告素材搜索和事件检索
- 体现搜索引擎和数据分析能力

任务：

- 建立广告素材索引
- 建立广告事件索引
- 素材新增或修改时同步 ES
- 事件消费时异步写入 ES
- 实现多条件搜索接口
- 实现事件检索接口

索引：

```text
ad_creative_index
ad_event_index_yyyyMMdd
ad_stats_index
```

接口：

- `GET /api/search/creatives`
- `GET /api/search/events`

验收标准：

- 可以按素材标题模糊搜索
- 可以按广告主、行业、状态过滤
- 可以按事件类型、时间范围检索事件
- ES 不可用时不影响核心投放链路

### 阶段 8：系统保护与稳定性

目标：

- 加强高并发场景下的系统稳定性

任务：

- Redis Lua 限流
- Kafka 消费异常重试
- 死信 Topic 设计
- 事件幂等处理
- MySQL 唯一索引保护
- 慢接口日志
- 核心业务日志

验收标准：

- 高频事件请求会被限流
- 重复消费不会产生重复统计
- 消费失败有错误日志
- 核心链路日志可追踪 `requestId` 和 `eventId`

### 阶段 9：Docker Compose 部署

目标：

- 将项目从本机依赖切换到容器化部署

任务：

- 编写 `Dockerfile`
- 编写 `docker-compose.yml`
- 编写 MySQL 初始化 SQL
- 配置 Redis 容器
- 配置 Kafka 容器
- 配置 Elasticsearch 容器
- 配置应用服务容器
- 提供 `application-docker.yml`

验收标准：

- `docker compose up -d` 能启动所有服务
- 应用能连上容器内 MySQL、Redis、Kafka、ES
- 初始化 SQL 自动执行
- README 中有完整启动和测试步骤

### 阶段 10：压测与项目整理

目标：

- 为简历和面试准备数据支撑

任务：

- 使用 JMeter、wrk 或 ab 压测事件采集接口
- 记录接口 QPS 和平均响应时间
- 观察 Kafka 消费延迟
- 观察 Redis 计数变化
- 优化慢 SQL
- 整理 README
- 整理接口文档
- 整理简历描述和面试讲解稿

验收标准：

- 有压测命令或压测截图
- 有关键接口性能数据
- README 能让别人运行项目
- 简历描述能体现技术亮点

## 6. 代码规范

### 6.1 命名规范

包名：

```text
com.example.adplatform.{module}
```

类名：

- Controller：`XxxController`
- Service 接口：`XxxService`
- Service 实现：`XxxServiceImpl`
- Mapper：`XxxMapper`
- 实体类：`XxxEntity`
- 请求对象：`XxxRequest`
- 响应对象：`XxxResponse`
- 视图对象：`XxxVO`
- 数据传输对象：`XxxDTO`

方法名：

- 查询单个：`getById`
- 分页查询：`pageQuery`
- 创建：`create`
- 更新：`update`
- 删除：`delete`
- 状态变更：`online`、`pause`、`offline`

### 6.2 分层规范

Controller：

- 只负责接收参数、参数校验、调用 Service、返回结果
- 不写业务逻辑
- 不直接操作 Mapper、Redis、Kafka

Service：

- 编排业务流程
- 处理事务
- 调用 Mapper、Redis、Kafka、ES 组件

Mapper：

- 只负责数据库访问
- 优先使用 MyBatis-Plus 通用方法
- 复杂 SQL 写 XML

DTO / VO：

- Request DTO 用于接收请求
- Response / VO 用于返回前端
- Entity 不直接作为接口返回值

### 6.3 接口返回规范

统一返回结构：

```json
{
  "code": 0,
  "message": "success",
  "data": {}
}
```

错误示例：

```json
{
  "code": 40001,
  "message": "参数校验失败",
  "data": null
}
```

### 6.4 参数校验规范

必须使用 Bean Validation：

- `@NotNull`
- `@NotBlank`
- `@Min`
- `@Max`
- `@Size`
- `@Valid`

不允许在 Service 中大量写重复的空值判断。

### 6.5 异常规范

统一使用业务异常：

```java
throw new BusinessException(ErrorCode.CAMPAIGN_NOT_FOUND);
```

禁止：

- 直接 `e.printStackTrace()`
- Controller 中到处 `try catch`
- 返回不统一的错误格式

### 6.6 日志规范

使用 SLF4J：

```java
private static final Logger log = LoggerFactory.getLogger(Xxx.class);
```

核心日志必须包含：

- `requestId`
- `eventId`
- `campaignId`
- `creativeId`
- 错误原因

禁止记录：

- 明文密码
- 真实敏感 Token
- 过大的完整请求体

### 6.7 事务规范

需要事务的场景：

- 创建广告计划并初始化相关数据
- 消费事件后写事件表和统计表
- 状态变更涉及多张表时

使用：

```java
@Transactional(rollbackFor = Exception.class)
```

注意：

- Redis、Kafka、ES 不和 MySQL 共用本地事务
- 跨资源一致性通过幂等、补偿和重试解决

### 6.8 Redis 使用规范

Redis Key 必须集中定义，不能散落硬编码。

建议创建：

```text
infra.redis.RedisKeyConstants
```

Key 必须包含业务前缀：

```text
ad:stats:rt:{date}:{campaignId}
```

需要设置 TTL 的 Key 必须明确过期时间。

### 6.9 Kafka 使用规范

Topic 名称集中定义：

```text
infra.kafka.KafkaTopicConstants
```

消息必须包含：

- `eventId`
- `eventType`
- `requestId`
- `campaignId`
- `creativeId`
- `userId`
- `eventTime`

消费者必须考虑：

- 重复消费
- 消费失败
- 反序列化失败
- 消息字段缺失

### 6.10 MySQL 设计规范

所有表必须包含：

- `id`
- `created_at`
- `updated_at`

金额统一使用整数，单位为分。

禁止使用浮点数存储金额。

状态字段使用字符串或小整数，但 Java 侧必须有枚举。

关键唯一约束：

- `ad_event.event_id`
- `ad_stats_daily(stat_date, campaign_id, creative_id)`

### 6.11 Elasticsearch 使用规范

ES 用于检索和分析，不作为核心事务数据库。

要求：

- 写 ES 失败不能影响广告投放主链路
- 事件索引按日期拆分
- keyword 字段用于精确过滤
- text 字段用于全文搜索

## 7. 测试规范

### 7.1 接口测试

每完成一个阶段，都要提供接口测试样例。

最低要求：

- 请求 URL
- 请求方法
- 请求 JSON
- 预期响应
- 数据库或 Redis 验证方式

### 7.2 单元测试

优先测试：

- 广告定向匹配逻辑
- 广告排序逻辑
- Redis Key 生成逻辑
- CTR、CVR 计算逻辑

### 7.3 集成测试

后期测试：

- MySQL 持久化
- Redis 计数
- Kafka 生产和消费
- ES 写入和搜索

## 8. Git 提交规范

提交信息格式：

```text
type: description
```

常用类型：

- `feat`: 新功能
- `fix`: 修复问题
- `docs`: 文档
- `refactor`: 重构
- `test`: 测试
- `chore`: 构建或配置

示例：

```text
feat: add campaign management api
docs: add development plan
fix: handle duplicated ad event consumption
```

## 9. 每阶段交付物

每个阶段完成后，都要留下以下内容：

- 可运行代码
- SQL 变更
- 接口测试记录
- 核心设计说明
- 已知问题
- 下一阶段任务

不能只写代码不说明设计原因。

## 10. 第一阶段开始前检查清单

本机环境：

- Java 17 已安装
- Maven 已安装
- MySQL 已启动
- Redis 已启动
- Kafka 可后续启动
- Elasticsearch 可后续启动

数据库：

```sql
CREATE DATABASE ad_platform DEFAULT CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci;
```

应用配置：

- 本地配置放在 `application-local.yml`
- 默认启动 profile 使用 `local`
- 不提交本机真实密码

启动命令：

```bash
mvn spring-boot:run -Dspring-boot.run.profiles=local
```

健康检查：

```text
GET /api/health
```

## 11. 简历导向开发原则

开发时每个模块都要能回答三个问题：

- 这个模块解决了什么业务问题？
- 为什么使用这个技术方案？
- 高并发或异常情况下怎么保证系统稳定？

重点体现：

- Kafka 异步解耦
- Redis 实时计数和限流
- 广告召回与排序算法
- 消费幂等
- MySQL 索引设计
- Elasticsearch 检索分析
- Docker Compose 部署能力
- 压测和性能优化意识
