P0 — 当前工作区编译/测试就过不去

1. 测试构造器与实现不同步
    - PlanServiceImplTests.java:25 传 6 参（含 EventMetadataPort），实现只剩 5 个字段
    - MaterialServiceImplTests.java:24、AdminCrudTests.java:102 传 5 参（含 PlanMapper），实现只剩 4 个字段
    - mvn test 在 test-compile 阶段即失败
2. 测试桩与实现不符：AdminCrudTests.java:105 用 advertisers.exists 打桩，但服务实际用 selectOne 查广告主；MaterialServiceImpl 的 create 已改成 slotMapper.exists（MaterialServiceImpl.java:58），测试桩仍打 selectOne。修好构造器后这些用例也会挂。

P0 — schema 与实体脱节（预存在，非本次 diff，但直接决定 CRUD 能否在新库上跑）

3. status 列在 DDL 里叫 bloomSnapshot：01-admin-schema.sql:10/26/44/61，advertiser/slot 是 TINYINT、plan 是 VARCHAR 存状态值（种子数据证实），索引名却还叫 idx_*_status。实体全部映射 status 字段（如 PlanEntity.java:72），无任何 @TableField 或迁移脚本对应。全新按脚本初始化后，plan create（converter 必写 OFFLINE）必报 Unknown column 'status'。疑似 1aa8662 布隆过滤器部分初步重写 的误改名，你本地库能跑大概率是因为库是改名前的旧结构。
4. advertiser_id 类型冲突：PlanEntity.java:43-44、MaterialEntity.java:40/46 把 String 型 publicId 直接映射进 advertiser_id/slot_id BIGINT 列。INSERT 'adv_xxx' 进 BIGINT 会报错（严格模式）或变 0；而种子数据用的是内部自增 id，两套约定并存。需确认意图：列改 VARCHAR 存 publicId，还是实体存内部 id。

P1 — 业务与校验

5. "同名去重"没有唯一约束支撑：plan/material 只有 uk_*_public_id，DuplicateKeyException → "已存在同名计划/素材"（PlanServiceImpl.java:70、MaterialServiceImpl.java:65）实际只会被 publicId 撞库触发并误报；同广告主同名可正常重复插入。（advertiser 有 uk_advertiser_name，无此问题）
6. 文案错误：PlanServiceImpl.java:115 计划删除 404 提示"素材不存在"。
7. plan update 不合并存量（PlanServiceImpl.java:93，selectOne 被注释）：只传 endTime 或 budgetDaily 时，UpdatePlanRequest 的 @AssertTrue 单侧为 null 直接放行，可落库 endTime < startTime、daily > total 的脏配置。
8. CreatePlanRequest.java:27-35 的 @AssertTrue 直接解引用 @NotNull 字段：缺 startTime/budgetTotal 时 NPE → ValidationException → 500，而不是 400。Update 版本有 null 保护，两边不一致。
9. billingType 由 normalize 改为大小写敏感的 @Pattern("CPC|CPM|CPA")：'cpc'、' CPM ' 现在直接 400；正则复制在 Create/Update 两个 record，新增计费方式要改两处。
10. plan update/delete 的 Outbox 与缓存失效被注释且位于 return 之后（PlanServiceImpl.java:103-105, 118-119），与 online/pause/offline 仍保留 outbox、类注释"所有影响投放的写操作都会追加 Outbox"矛盾。若按"暂不考虑缓存组件"有意裁剪，建议同步类注释，否则改 bidPrice/budget 后投放侧仍按旧值。
11. DRAFT 已从枚举删除，但 DDL 默认值仍 'DRAFT'（01-admin-schema.sql:44）、种子 plan 4 仍是 'DRAFT'（99-seed-demo-data.sql:47）；新建默认 OFFLINE，种子行会展示无枚举对应的状态。
12. 删除无关联校验：advertiser 删除不查下属 plan/material（产生孤儿数据）；plan 删除时若有 rule 引用（uk_rule_plan）直接 FK 异常 500，无友好提示。

P2 — 卫生/一致性

13. 未使用 import：PlanServiceImpl（MaterialEntity、EventMetadataPort、MaterialOperationResponse）、MaterialServiceImpl（MaterialAuditStatus、PlanMapper）。
14. PlanConverter.java:39-47 注释掉的 MapStruct 方法依赖已删除的 BillingType.normalize，恢复不可行，建议删除。
15. MaterialServiceImpl create 用 slotMapper.exists、update 用 selectOne，校验实现不一致。
16. AdvertiserServiceImpl.update 拒绝"已停用"广告主的一切修改（改资料都不行）；create/update/delete 无 @Transactional。
17. 小格式：try{、if( 缺空格（PlanServiceImpl.java:68、MaterialServiceImpl.java:58）。

其中 3、4 两条是预存在的 schema 问题，但恰恰是基础 CRUD 在新环境初始化时的致命项，建议优先确认