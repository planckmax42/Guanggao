# Admin 雪花 ID 迁移实施计划

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:executing-plans，按任务顺序在当前会话执行。此文件是计划，当前未授权开始实施。

**Goal:** 将 admin 的 advertiser、material、plan、rule、slot 从自增内部 ID + UUID publicId 迁移为单一雪花 Long ID。

**Architecture:** 使用 MyBatis-Plus 3.5.7 的 ASSIGN_ID 和显式配置节点编号的 DefaultIdentifierGenerator。实体及关联字段使用 Long，HTTP ID 使用十进制字符串表达；Converter 仍保持接口，Service 保留当前业务校验和捕获转换异常的风格。

**Tech Stack:** Java 17、Spring Boot 3.3、MyBatis-Plus 3.5.7、MapStruct、Jackson、JUnit 5、Mockito。

**Spec:** 本会话用户指令：弃用 publicId，改用雪花主键；本阶段只改 admin 代码，数据库与其他组件随后处理；当前先给计划。

## 范围约束

- 本阶段不执行 DDL、不重编号现有数据库记录、不编写数据迁移任务。
- 不改 tracking、delivery、report、search、infra 的实现，不做伪兼容：不能把雪花 ID 的字符串塞进旧 publicId 语义的接口。
- 不删除 common/util/PublicIdUtils.java；外部模块还可能使用 UUID 事件标识。本阶段只消除 admin 管理链路对它的依赖。
- Create 必填、Update 可选，更新的目标 id 必填；业务字段的既有语义、金额单位、设备列表语义保持不变。
- 不额外增加一列 snowflakeId；改用现有 id 列，Java 类型保持 Long。
- 本阶段 API 是破坏性变更：publicId 等旧字段不再作为管理资源标识。
- 计划没有要求保留旧库即刻可运行；必须等数据库及跨模块迁移完成才能验证整个系统。

## 已确认的跨模块边界

用户已选择：暂时保留跨模块接口，先完成 admin 管理接口和 CRUD 的迁移。

保留跨模块专用 Mapper/Port 及联表投影的现有签名、SQL 与语义。这些代码仍依赖旧数据库契约，不属于本阶段可用性验收范围。report 对已删除实体 getter 的编译依赖依然存在，不能宣称全项目兼容。

## 审查重点

1. 大于 JavaScript 安全整数范围的 ID 必须以字符串返回，不能对预算、金额、分页 total 等所有 Long 一刀切。
2. 请求中的小数、非数字、溢出 ID 不得被截断或转成其他资源 ID；非法参数返回 400。
3. Create 不接受客户端指定主键；更新的主键及广告主归属不被 Converter 意外覆盖。
4. 不同独立部署必须分配不同的 datacenterId/workerId 组合；不把默认节点推断当作跨环境唯一保证。
5. Plan 状态接口仍有活跃 Outbox 调用，Slot 状态接口仍有缓存调用；阶段性断联必须明示。

## Task 1：生成策略和主键

**涉及文件：**
- 新增 `src/main/java/com/example/adplatform/admin/config/AdminIdConfiguration.java`
- 修改 `src/main/java/com/example/adplatform/admin/entity/AdvertiserEntity.java`
- 修改 `src/main/java/com/example/adplatform/admin/entity/MaterialEntity.java`
- 修改 `src/main/java/com/example/adplatform/admin/entity/PlanEntity.java`
- 修改 `src/main/java/com/example/adplatform/admin/entity/RuleEntity.java`
- 修改 `src/main/java/com/example/adplatform/admin/entity/SlotEntity.java`
- 新增 `src/test/java/com/example/adplatform/admin/config/AdminIdConfigurationTests.java`

- [x] 先覆盖配置缺失/节点编号越界失败、显式节点生成正数 ID、批量生成无重复、真实 MyBatis insert 前回填主键。
- [x] 使用 `new DefaultIdentifierGenerator(workerId, datacenterId)` 注册 IdentifierGenerator。配置键为 `admin.id.worker-id`、`admin.id.datacenter-id`，均显式提供，当前实现有效范围 0～31。测试提供固定值；真实部署编号需由部署者统一分配。
- [x] 不修改全局主键策略。五个实体逐个使用：

```java
@TableId(type = IdType.ASSIGN_ID)
private Long id;
```

- [x] 删除五实体的 publicId 字段、initializePublicId 及仅服务于该字段的导入；保留其他字段与 JSON 类型处理器。
- [x] 检查本次生成器 Bean 的作用域：目前没有其他 ASSIGN_ID/IdentifierGenerator；后续模块采用它时必须使用同一分配规则。
- [x] 删除五个 create 方法中的 UUID 初始化，主键由 insert 回填，插入成功后返回 entity.id。

## Task 2：关联字段与 HTTP 契约

**涉及文件：** `admin/request`、`admin/response` 下 advertiser/material/plan/rule/slot 的 records，以及五个实体、五个 Converter。

| 当前字段 | 新字段 | Java 类型 |
|---|---|---|
| publicId | id | Long |
| advertiserPublicId | advertiserId | Long |
| slotPublicId | slotId | Long |
| planPublicId | planId | Long |
| materialPublicId | materialId | Long |

- [x] Material 的 advertiserId、slotId，Plan/Rule 的 advertiserId 与对应数据库 `_id` 列统一映射为 Long。
- [x] 创建请求不增加资源自身 id。关联必填 ID 使用 `@NotNull @Positive Long`；更新目标、删除、审核、状态切换 ID 同样必填；可选更新关联使用 `@Positive Long`。
- [x] 移除 ID 前缀正则及对 ID 的 blankToNull；其他业务字符串的规范化保留。
- [x] ID 请求使用 Long，通过 admin 内专用 `AdminIdDeserializer` 严格读取十进制整数字符串或整数 token，拒绝小数/布尔/溢出；返回 ID 在 record 分量使用 `@JsonSerialize(using = ToStringSerializer.class)`，不注册全局 Long 序列化器。
- [x] 五个 Converter 删除 publicId 忽略项；create/update 继续忽略目标 id；广告主归属更新仍忽略 advertiserId。OperationResponse、QueryResponse、PlanResponse、RuleResponse、AvailableSlotResponse 的 ID 字段全部同步。
- [x] 参数与序列化测试包括：

```java
assertThat(json.readTree(json.writeValueAsString(new RuleOperationResponse(1987654321098765432L)))
        .get("id").asText()).isEqualTo("1987654321098765432");
assertThat(json.readTree(json.writeValueAsString(new RuleOperationResponse(1987654321098765432L)))
        .get("id").isTextual()).isTrue();
```

- [x] 同时验证必填 null、0、负数、旧 `rule_...` 字符串、小数、超 Long 范围值均被拒绝；可选关联 null 仍允许；金额/预算仍为数字。

## Task 3：CRUD、归属校验、状态操作

**涉及文件：** 五个 `admin/service/impl/*ServiceImpl.java`、`admin/util/AdvertiserGuardUtils.java`、五个 `admin/controller/*Controller.java` 及相关服务接口。

- [x] `AdvertiserGuardUtils.requireEnabled(Long advertiserId)` 改用 `selectById`，保留不存在/停用错误。
- [x] 单条查询改用 `selectById(request.id())`，删除使用 `deleteById(request.id())`，更新继续 fresh entity + `updateById`。
- [x] 返回值使用请求的 id 或插入后实体 id；移除内部 id/公开 id 两套变量转换。
- [x] 计划上线/暂停/下线、Slot 状态操作、素材审核请求的 ID 一并迁移，保持原状态约束。
- [x] 现有重复名称、外键冲突、无变化不写库、合并年龄/预算/时间校验不改。
- [x] 在五个现有 ServiceImplTests 中验证创建 ID 回填、按 Long ID 查改删、重复更新、零行更新、同广告主名称冲突、广告主停用、关联不存在。

## Task 4：跨模块边界

**确切依赖：**
- `admin/mapper/MaterialMapper.java` 的三个 publicId 查询由 Redis 元数据与 Bloom 实现调用。
- `admin/query/MaterialPlanJoinRow.java` 同时暴露 materialId/materialPublicId。
- `admin/port/EventMetadataPort.java` 被 infra 的 MaterialMetadataRedisServiceImpl 实现。
- report/ReportServiceImpl 直接访问 PlanEntity、MaterialEntity、SlotEntity 的 getPublicId。
- PlanServiceImpl.online/pause/offline 仍调用 SearchOutboxService。
- SlotServiceImpl.updateStatus 仍调用缓存、过滤器和停投事件。

- [x] 保留 MaterialMapper 的 selectMaterialPlanByPublicId、selectAllMaterialPublicIds、selectMaterialPublicIdsByPlanId，以及 MaterialPlanJoinRow、EventMetadataPort，不改其参数类型、返回类型、SQL 或字段含义。
- [x] 保留 PlanMapper.selectPlanRuleRows 和 PlanRuleJoinRow 的旧联表契约；其旧 r.plan_id、region、user_tags 引用在后续查询组件迁移时处理。
- [x] 五个 BaseMapper 的普通 CRUD 随实体映射迁移；遗留自定义 SQL 不作为新数据库可用功能交付。
- [x] 提议注释 Plan 状态方法中仍使用旧 publicId 契约的 Outbox 调用，保留说明，不能将 Long.toString() 冒充旧 publicId。这项暂停作为计划中的显式行为变更随计划确认；尚未执行。
- [x] Slot 的 slotCode 是业务编码，保留；已使用 Long/slotCode 的端口不因 ID 迁移而改签名，不能据此宣称下游已兼容新数据库。
- [x] 输出外部断点清单；不删除仍被外部模块使用的 common 工具和响应类型。

## Task 5：验证与交付

- [x] 更新 admin 下 Controller、request、Converter、Service、Mapper 测试中的 UUID 样例为有效 Long ID；JSON 样例中的 ID 使用字符串。
- [x] Mapper 测试使用实际生成的 SQL，验证五实体 insert 回填 ID、不出现 public_id、关联参数为 Long，以及 Rule JSON 数组读写不变。
- [x] 运行 admin 定向测试与 `mvn test`。若全项目被外部旧调用阻塞，隔离编译 admin 必要源码及真实依赖，报告独立测试范围，不伪报全量通过。
- [x] 对比实施前后编译错误，区分既有 ReportConverter/AdDeliveryConverter/EventConverter 错误与本次删除旧契约导致的预期断点。
- [x] 执行 `git diff --check`，核对生产变更仅在 admin；计划文档和 admin 测试可同步更新。
- [x] 交付新 API 字段说明、节点编号部署要求、数据库对齐清单及跨模块后续迁移清单。

数据库对齐清单仅用于下一阶段：五个主键及关联列为 BIGINT；去除 AUTO_INCREMENT 和 public_id；保留按 advertiser_id + name 等业务唯一约束。现有自增记录如何重编号和关联重写另作数据库迁移方案，本阶段不执行。

---

## 交付清单（2026-11-13 验证结果）

### 验证结果

- 隔离编译范围：common + admin + 3 个跨模块类型（ConfigStopGuardEvent、ConfigAggregateType、EventMaterialMetadata），`javac --release 17` 主/测试切片编译通过。
- 隔离运行 admin 测试：37 个测试容器、**147 个测试全部通过**（含 4 个新增 ID 测试类）。
- `git diff --check` 通过；生产 Java 变更全部位于 admin；唯一例外是 application-local.yml 新增 `admin.id.*` 本地配置。
- 全项目 `mvn test` 仍不可用（见断点清单），与计划预期一致，不伪报全量通过。

### 编译断点清单（全项目）

既有错误（HEAD 基线 f6b0f28 即存在，与本次迁移无关）：

- AdDeliveryConverter：AdItemResponse 无 advertiserPublicId、name 属性；
- EventConverter：EventMaterialMetadata 无 planId/materialId；EventEntity 无 slotPublicId；
- ReportConverter：无 advertiserPublicId 源参数；
- 剥离上述 MapStruct 错误后，javac 层还暴露 delivery/AdDeliveryServiceImpl、infra/RedisEventStatisticsStore 的既有符号错误，未进一步深挖。

本次迁移新增的预期断点（按计划不在本阶段修复）：

- AdDeliveryConverter 引用已删除的 `plan.publicId` / `material.publicId`；
- report/ReportServiceImpl 4 处 `PlanEntity::getPublicId` / `MaterialEntity::getPublicId` / `SlotEntity::getPublicId`。

### 节点编号部署要求

- 启动必须提供 `admin.id.worker-id`、`admin.id.datacenter-id`（有效范围 0～31），缺失或越界时上下文启动失败（有测试固化该行为）。
- application-local.yml 已提供本地默认值（环境变量覆盖：`ADMIN_ID_WORKER_ID` / `ADMIN_ID_DATACENTER_ID`）；多部署合库前，部署者必须为每个部署分配不同组合。

### API 变更说明

- 资源标识 `publicId` → `id`（Long，JSON 输出为十进制字符串）；关联字段 `advertiserPublicId/slotPublicId/planPublicId/materialPublicId` → `advertiserId/slotId/planId/materialId`。
- Create 不接受客户端主键；更新/删除/审核/状态切换的目标 id 必填；请求侧 ID 仅接受十进制整数字符串或整数 token，小数、布尔、溢出返回 400。
- 金额、预算、分页 total 等业务数值仍为数字类型，不受字符串化影响。

### 跨模块后续迁移清单

- 数据库对齐：五表主键及关联列改 BIGINT、去 AUTO_INCREMENT 与 public_id；现有自增数据重编号与关联重写另作迁移方案（本阶段未执行）。
- delivery：AdDeliveryConverter、AdDeliveryServiceImpl 改用 Long id。
- report：ReportServiceImpl 四处 getPublicId、ReportConverter 一并修复。
- tracking：EventConverter 既有错误一并修复。
- search/infra：Outbox、ES 文档、MaterialMapper 三个 publicId 查询、MaterialPlanJoinRow、EventMetadataPort 按新契约迁移后，恢复 Plan/Rule/Slot 已注释的 Outbox 与缓存同步。
- 未删除 common/util/PublicIdUtils.java，外部模块 UUID 事件标识仍在使用。
