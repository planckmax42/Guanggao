# Controller 请求体统一改造

**目标：** 按用户要求，将所有有业务入参的 Controller 方法统一为 `@Valid @RequestBody XxxRequest request`。

**实现：** 保持 Java 17 record 和现有 Service 接口。GET 查询改 POST；PUT 更新保留 PUT，路径 ID 移入请求体。无参健康检查和索引重建保持不变。保留已有参数约束和分页默认值，不恢复用户删除的广告主查询字段。

## 实施清单

- [x] 增加 Controller 回归测试，覆盖 JSON 参数绑定、分页默认值、分页范围、必填 ID、必填日期和错误 ID 格式；先运行测试确认现状。
- [x] 在 `admin/request/{slot,plan,material,rule}` 增加分页和单资源请求，修改对应 Controller；扩展 UpdatePlanRequest、AuditMaterialRequest，将原路径校验移入 DTO。
- [x] 在 `report/request` 增加 DailyReportRequest、FunnelReportRequest、TopMaterialsRequest，改造 ReportController。
- [x] 同步 `jmeter/ad-platform-load-test.jmx`、`docs/DEVELOPMENT.md`、`SearchPipelineIT` 的请求地址和请求体。
- [x] 运行相关测试及 `mvn test`，检查 Controller 不再使用 RequestParam/PathVariable；报告实际编译或测试阻碍。

## 检查重点

- 更新接口的 publicId 必填并验证资源类型；已有请求体的其他字段校验保持原样。
- 分页原有默认值为 current=1、size=10，范围 current>=1、1<=size<=100。
- 报表日期从 JSON 绑定为 LocalDate，必填日期缺失返回参数错误。
- 可选过滤项仍可省略，报表 limit 的默认值和上限仍由 Service 处理。
- 使用新路由的调用示例、压测脚本和集成测试同步迁移。

## 验证结果

- 使用临时 Maven 配置 `/tmp/guanggao-controller-check/pom.xml`，直接编译仓库中的 Controller、Request、Service 接口和响应类型；36 项新增 MockMvc 测试通过。
- 原实现运行这些测试时有 33 项失败；迁移请求体后全部通过。
- `git diff --check` 通过，JMeter XML 和受影响请求的 JSON 均解析通过。
- Controller 中已无 RequestParam、PathVariable、RequestHeader、ModelAttribute；仅无参健康检查保留 GET。
- 全项目 `mvn test` 仍在编译阶段被已有 AdvertiserConverter、PlanConverter、MaterialConverter 的 lockAcquireResult 映射错误阻断，未能运行全量测试或 SearchPipelineIT。
