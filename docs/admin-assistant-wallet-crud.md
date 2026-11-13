# 副账号与钱包基础 CRUD

沿用 admin 现有 Controller → Service → Mapper、MapStruct Converter、record 请求响应以及统一 Result 结构。

| 模块 | 基础路径 |
| --- | --- |
| advertiser_assistant | `/api/advertiser/assistants` |
| advertiser_wallet | `/api/platform/advertiser-wallets` |

两个模块均提供 `POST /create`、`POST /page`、`PUT /update`、`DELETE /delete`，请求使用 JSON。新增、修改、删除操作成功时，统一 Result 的 data 为 `{"id":"1987654321098765432"}`。

## 副账号

创建请求：

```http
POST /api/advertiser/assistants/create
Content-Type: application/json

{"advertiserId":"1987654321098765432","username":"operator01","name":"运营人员","role":"OPERATOR"}
```

四个字段必填；用户名、姓名最长 64 字符，角色最长 32 字符。角色目前只保存字符串，不实现权限逻辑。创建时默认启用。用户名唯一性依赖数据库唯一索引。

部分更新：

```http
PUT /api/advertiser/assistants/update
Content-Type: application/json

{"id":"1987654321098765433","name":"新姓名"}
```

可修改 username/name/role；省略或 null 不修改，空白字符串按现有 admin 约定转为 null。至少提供一个有效修改字段。同值提交返回成功且不写库，不允许修改所属广告主和状态。

查询记录包含 id、advertiserId、username、name、role、status。**本阶段不加入密码字段、密码处理或登录功能。**

## 钱包

创建请求仅包含所属广告主：

```http
POST /api/platform/advertiser-wallets/create
Content-Type: application/json

{"advertiserId":"1987654321098765432"}
```

balanceMicro、frozenMicro 均初始化为 0。每个广告主只能有一个钱包，依赖数据库 advertiser_id 唯一索引。

部分更新：

```http
PUT /api/platform/advertiser-wallets/update
Content-Type: application/json

{"id":"1987654321098765434","balanceMicro":1000000}
```

可修改 balanceMicro/frozenMicro，必须传 JSON 整数且不能为负数，0 是有效更新值；拒绝小数、布尔、字符串和超 Long 范围值。单位沿用数据库 micro，金额为绝对值设置，不是增减金额。省略字段保持原值；同值提交不写库；不允许更换广告主。本阶段不实现充值、扣费、冻结解冻或账务流水。

查询记录包含 id、advertiserId、balanceMicro、frozenMicro。金额保持 JSON 数字。

## 共同约定

分页请求：`{"current":1,"size":10}`，两字段必填，current >= 1，1 <= size <= 100；不增加筛选参数，按 id 降序返回。

分页 data 示例：

```json
{"current":1,"size":10,"total":1,"pages":1,"records":[{"id":"1987654321098765434","advertiserId":"1987654321098765432","balanceMicro":1000000,"frozenMicro":0}]}
```

删除请求：`{"id":"1987654321098765434"}`。删除不存在的记录（包括重复删除）返回 RESOURCE_NOT_FOUND；数据库外键阻止删除时返回 RESOURCE_IN_USE。

ID 使用应用雪花生成器；创建不接受客户端主键。请求 ID 允许十进制整数字符串或整数，拒绝小数、布尔、旧 publicId 和超 Long 范围值。响应 id、advertiserId 为字符串。

创建时检查广告主存在且启用；数据库唯一键冲突返回 DUPLICATE_RESOURCE，外键关联缺失返回 RESOURCE_NOT_FOUND，其他数据库异常保留原有异常处理。

## 数据库同步边界

本次不修改 SQL，也未写入数据库。后续需对齐两表雪花主键和关联列、唯一约束、外键约束。旧副账号表的 password_hash 为无默认值的 NOT NULL 列，与本次不包含密码的创建接口不兼容，需在数据库同步阶段处理，不能直接对旧结构验收创建接口。
