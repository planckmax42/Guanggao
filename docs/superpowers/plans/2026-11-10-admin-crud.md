# Admin CRUD 实现计划

目标：按现有分层补齐 advertiser、material、plan、rule、slot 的 CRUD。

约束：分页只接收 current、size；保留新增状态约定，普通更新不写状态；保留 plan 的 online、pause、offline 状态流转逻辑，仅适配公开标识关联；保留工作区已有改动。基于当前 admin 实体，不在本次重构数据库及其他业务域。

- [x] 补回归测试：无筛选分页、更新不写状态、创建关系检查、删除成功及重复删除、规则创建冲突。
- [x] 补 Request、Response、Converter、Service 和 Controller；写操作返回公开标识，分页返回 PageResponse。
- [x] 保留 slot 缓存维护及 plan/rule 既有同步职责。
- [x] 执行针对性测试及 mvn test，记录原有编译阻塞，检查三个 plan 状态方法未改动。

补充约定：admin 外键统一为 publicId；方法按 CRUD 排列；Converter 沿用 AdvertiserConverter 命名。接口及验证限制见 `docs/admin-crud.md`。
