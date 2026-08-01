# CLAUDE.md

This file provides guidance to Claude Code (claude.ai/code) when working with code in this repository.

## 项目概述

广告投放与实时数据分析平台，Spring Boot 3.3 单体应用（Java 17 + Maven + MyBatis-Plus），按业务域拆包。

## 构建与运行

```bash
# 编译 + 测试
mvn clean package

# 仅测试
mvn test

# 运行单个测试类
mvn -Dtest=XxxTests test
mvn -Dtest=SearchPipelineIT test   # 集成测试，需本地 MySQL + Redis + ES

# 启动应用（默认 local profile）
mvn spring-boot:run -Dspring-boot.run.profiles=local

# Docker 服务
docker compose -f docker-compose.elasticsearch.yml up -d   # ES + Debezium Connect
docker compose -f docker-compose.logging.yml up -d          # Grafana + Prometheus + Loki
```

健康检查：`curl http://127.0.0.1:8080/api/health`

## 核心架构

### 投放链路（核心流程）

```
投放请求 → ES 静态粗召回 → Redis 动态过滤 → Java 精排 → 返回广告
              ↓ 异常/熔断
         MySQL 降级召回
```

1. **SlotCacheService**：先校验广告位编码有效性（布隆过滤器 → Redis → MySQL 三级查找，带条带锁和熔断保护）
2. **CandidateRecallService**：通过 ES 读别名 `ad-candidate-read` 执行多维粗召回（广告位/时间/地域/设备/性别/年龄/标签）。只有 ES 异常/超时/熔断时才降级到 MySQL；ES 正常返回空集不回源
3. **静态度校验**：索引快照状态校验 + **DeliveryStopGuardService** 的 Redis stopped set 堵住 ES 最终一致窗口
4. **动态过滤**：BudgetRedisService（预算）、FrequencyRedisService（用户频控）批量查 Redis
5. **精排**：`score = bidPrice * 0.7 + CTR * 1000 * 0.2 + qualityScore * 0.1`（冷启动 CTR 先验 2%），读取 `daily_report` 表当天的曝光/点击指标

### 数据同步链路（MySQL → ES）

```
配置变更 → MySQL + Outbox 同事务提交 → Debezium 读 Binlog → Kafka → SearchIndexKafkaConsumer → ES 写别名
```

- **Debezium Outbox Event Router** 从 `outbox` 表捕获变更，发布到 `ad-config-change` topic
- **CandidateIndexSyncService**：按聚合 key（素材/计划/定向/广告位）从 MySQL 读取全量当前状态，幂等写入 ES 写别名
- Outbox 轮询模式保留为回退：设置 `OUTBOX_TRANSPORT=polling`
- 紧急停投先写 Redis stopped set，覆盖 CDC 链路的时间窗口

### 事件处理链路

```
曝光/点击/转化 → POST /api/tracking/events → Kafka event-topic
  → EventArchiveKafkaConsumer      → MySQL event 表（幂等归档）
  → EventBillingKafkaConsumer      → MySQL 计费流水（CPC/CPM/CPA）
  → EventStatisticsKafkaConsumer   → Redis 实时计数器 + MySQL daily_report
```

- Kafka 生产者：异步发送 + 重试 + 熔断（Resilience4j），broker ack 确认后才返回调用方
- 消费者使用条带锁（1024 条带） + 事务内写入保证幂等
- 三个消费者组均 `autoStartup=false`，由 `EventConsumerGroupOffsetInitializer` 迁移旧 offset 后启动

### ES 索引管理

- 版本化索引：`ad-candidate-yyyyMMddHHmmssSSS`
- 读写分离别名：`ad-candidate-read` / `ad-candidate-write`
- 全量重建：新索引批量写入 → refresh → 原子切换别名 → 旧索引保留可回滚
- Redis 锁防止并发重建
- 启动时 `ElasticsearchBootstrapRunner` 自动检测：read alias 缺失则从 MySQL 全量构建索引；ES 不可用时应用仍能启动，投放降级到 MySQL

### 通用韧性模式

- **布隆过滤器**：Guava 双缓冲区（active/standby），支持动态扩容重建，用于拦截不存在的广告位编码和事件 ID
- **条带锁**：`SlotCacheLockManager`、`EventMetadataCacheLockManager`，1024 条带细粒度并发控制
- **单航班请求合并**（single-flight）：`ConcurrentHashMap<CompletableFuture>`，相同 key 的并发回源合并为一次 MySQL 查询
- **Lua 脚本**：`try-charge.lua`（预算扣减）、`record-event.lua`/`record-cost.lua`（事件统计）、`pop-daily-stats.lua`（定时 flush）
- **配置来源**：`application.yml` 只放框架配置，业务配置全部在 `application-local.yml` 的 `app.*` 下

## 关键模块

| 包 | 职责 |
|---|---|
| `admin` | 广告主/计划/素材/定向规则 CRUD 管理 |
| `delivery` | 广告投放核心编排（召回+过滤+排序） |
| `tracking` | 事件上报 → Kafka 异步解耦 |
| `search` | ES 候选索引管理、查询、增量同步、消费者 |
| `report` | 实时统计、日统计、漏斗、素材排行、定时聚合 |
| `infra` | Redis（预算/频控/统计/广告位缓存/限流）、Kafka（生产者+消费者+熔断）、ES client |
| `common` | 统一响应体 `Result<T>`、全局异常处理、枚举、MyBatis 配置 |

## 测试规范

- JUnit 5 + AssertJ + Mockito + Spring Boot Test
- 普通单元测试：`*Tests.java`；依赖外部服务的集成测试：`*IT.java`
- 测试包结构镜像 main 包结构
- 新功能需覆盖成功、失败和幂等场景

## 代码规范要点

- 金额单位：分（整数），禁用浮点数
- Controller 只做参数校验 + 调 Service；业务编排和事务放 Service
- 统一返回：`Result<T>` → `{"code": 0, "message": "success", "data": {}}`
- 业务异常：`throw new BusinessException(ErrorCode.xxx)`
- Redis Key 集中定义在 `RedisKeyConstants`，必须含业务前缀 + TTL
- Kafka Topic 集中定义在 `KafkaTopicConstants`
- 消费者必须处理重复消费和反序列化失败
- Git 提交：`type: description`（feat/fix/docs/refactor/test/chore）
