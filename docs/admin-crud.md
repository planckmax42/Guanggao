# Admin CRUD 接口

五个模块均按 C → R（分页）→ U → D 排列 Controller、Service 和 ServiceImpl 方法，状态接口放在 CRUD 之后。Converter 统一使用 `createRequestToEntity`、`updateRequestToEntity`、`entityToQueryResponse`、`pageToResponse`、`entityToOperationResponse`。

| 模块 | 基础路径 |
| --- | --- |
| advertiser | `/api/platform/advertisers` |
| material | `/api/advertiser/materials` |
| plan | `/api/advertiser/plans` |
| rule | `/api/advertiser/rules` |
| slot | `/api/platform/slots` |

各路径提供 `POST /create` 新增、`POST /page` 分页、`PUT /update` 修改、`DELETE /delete` 删除，参数均使用 JSON 请求体，响应继续使用 `Result`。

分页只包含 `current` 和 `size`，两项均必传且不能为 null，size 上限 100，不支持筛选。

```http
POST /api/platform/advertisers/page
Content-Type: application/json

{"current":1,"size":10}
```

分页响应的 data 结构：

```json
{"current":1,"size":10,"total":0,"pages":0,"records":[]}
```

删除只传 publicId，删除不存在的记录返回 RESOURCE_NOT_FOUND（重复删除同样返回该错误），不级联删除其他业务数据。

```http
DELETE /api/platform/advertisers/delete
Content-Type: application/json

{"publicId":"adv_00000000000000000000000000000001"}
```

新增、修改和删除响应的 data 为 `{"publicId":"..."}`；plan 延续现有 `PlanOperationResponse`，字段为 `planPublicId`。

```http
PUT /api/platform/advertisers/update
Content-Type: application/json

{"publicId":"adv_00000000000000000000000000000001","name":"示例广告主","industry":"科技","contactName":"张三","contactEmail":"contact@example.com"}
```

普通修改不写 status，素材修改也不写 auditStatus 和 advertiserPublicId。五个 CRUD 修改请求仅 publicId 必传；其他字段省略或传 null 时不修改，传入非 null 值时按字段约束校验并更新。rule 的 regions、deviceTypes、userTags 传 [] 可清空对应定向。修改规则 planPublicId 时校验目标计划存在且未绑定其他规则。预算、时间和年龄范围按请求与原记录合并后的值校验。新增与分页的必填规则不变，独立状态接口不变。

例如只修改素材名称：

```http
PUT /api/advertiser/materials/update
Content-Type: application/json

{"publicId":"mat_00000000000000000000000000000001","name":"新素材名称"}
```

admin 实体外键值统一为公开标识：material 的 advertiserPublicId、slotPublicId，plan 的 advertiserPublicId，rule 的 planPublicId。数据库脚本未调整，现有 `@TableField` 仍映射到原来的 `*_id` 列，后续需同步调整数据库列类型/名称和其他业务链路。旧 `/rules/by-plan` 接口改为统一分页接口。

plan 的 online、pause、offline 状态流转逻辑保留，只适配 advertiserPublicId 的查询。

## 验证范围

- `mvn test`：全项目仍被原有 AdDeliveryConverter、ReportConverter、EventConverter 的字段映射编译错误阻塞。
- admin 单元测试在 `/tmp/guanggao-admin-crud-verification` 隔离目录执行，使用当前 admin 源码和项目 Maven 依赖；Outbox 服务在测试中使用 mock。临时副本去掉了 SearchOutboxService 调用已不存在的 `setMessageType` 方法这一原有编译阻塞，仓库内该文件未修改。
- 隔离验证覆盖 Controller 参数与路由、分页必填校验、Converter、关联存在性、修改不写状态、重复修改、删除不存在记录和 slot 缓存调用；不代表数据库、Redis、ES 或 Outbox 集成验证通过。

CRUD update 使用 publicId 条件更新，并检查 Mapper 返回值；0 表示记录不存在。advertiser、material 不再预查当前记录，plan、rule、slot 保留合并校验和缓存同步需要的旧值查询。默认 MySQL URL 显式设置 `useAffectedRows=false`；部署若通过 MYSQL_URL 覆盖，须保持该参数为 false，以按匹配行数判断。
