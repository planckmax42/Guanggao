# Admin 副账号与钱包基础 CRUD

**目标：** 按现有 admin 五模块风格补齐 advertiser_assistant、advertiser_wallet，仅修改代码，不执行或修改数据库脚本。

**设计：** Controller → Service/ServiceImpl → BaseMapper，MapStruct Converter，record Request/Response，统一 Result/PageResponse。用户已明确副账号不加入密码部分。沿用现有 CRUD 结构，不抽象通用 CRUD 父类。

**契约：**
- 两实体使用 Long 雪花主键，不包含 publicId；请求 ID 使用 AdminIdDeserializer，响应 ID 使用字符串。
- 副账号 `/api/advertiser/assistants`：创建 advertiserId、username、name、role；status 默认启用。更新只接受 username/name/role，空白按现有风格视为未提供，不允许换广告主或改状态。不加入密码字段、登录、密码处理。
- 钱包 `/api/platform/advertiser-wallets`：创建仅 advertiserId，balanceMicro/frozenMicro 默认 0；更新可分别设置非负整数金额（绝对值），不更换广告主、不引入币种或充值扣费流程。沿用现有表只有 updatedAt 的定义。
- 均提供 POST create/page、PUT update、DELETE delete；分页 current/size 必填，size <= 100，按 id 降序。
- 创建校验广告主存在且启用；唯一键、外键异常按现有工具转换；无变化更新不写库；删除不存在记录报错。
- 数据库后续需同步雪花主键、移除副账号必填密码列约束（或另行定义密码生命周期），保留 username/advertiser_id 的唯一约束。本次不实现数据库兼容补丁。

**实施与验证：**
- [x] 先编写新模块服务测试，验证缺少模块时编译失败；补充 HTTP 参数/序列化和 Mapper 测试。
- [x] 实现两个模块实体、Mapper、Request/Response、Converter、Service、Controller。
- [x] 隔离编译真实 admin/common 源码，运行所有 admin 测试；运行全项目 mvn test 并报告已有编译断点。
- [x] 核对仅涉及本次代码、补充接口示例、运行 git diff --check。

**基线：** 改动前 mvn -o test 已因 AdDeliveryConverter、ReportConverter、EventConverter 的 7 个 MapStruct 错误失败。不会为了运行本次测试修改这些模块。

## 验证结果

- 使用项目 Maven 测试依赖，javac --release 17 编译真实 admin/common 和三个已有跨模块类型，MapStruct/Lombok 正常生成代码；JUnit Launcher 运行 admin 全部测试，187/187 通过，其中本次新增 40 项测试，并扩展原有 Mapper 雪花 ID 测试覆盖两张新表。
- 测试不连接真实数据库。Mockito 使用启动时 Java agent，避免沙箱禁止动态 attach；没有更改生产源码或 mock 生产服务来绕过编译。
- 全项目 mvn -o test 仍被相同的 7 个 MapStruct 编译错误阻塞；未声称全项目测试通过。
- 独立审查确认钱包普通 Long 接收小数时可能静默截断。先增加 HTTP 回归测试，-0.5/1.5 实际返回 200 导致测试失败；加入仅作用于钱包金额字段的 WalletAmountDeserializer 后通过，不影响现有模块反序列化。
- git diff --check 通过，新增文件额外检查无行尾空格；未修改 SQL、未连接或写入数据库、未提交代码。
- 接口文档：docs/admin-assistant-wallet-crud.md。
