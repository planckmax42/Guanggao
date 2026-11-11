1. 计划创建的状态映射错误。
   PlanConverter.java:22 (src/main/java/com/example/adplatform/admin/converter/
   PlanConverter.java:22) 使用 CommonStatus.ENABLED，但 Mapper 未声明对应导入，而
   且计划的 status 是字符串，应使用计划状态，例如 PlanStatus.DRAFT.name()。当前写
   法存在编译问题。

2. 计划更新成功后，响应的 publicId 为 null。
   PlanServiceImpl.java:106 (src/main/java/com/example/adplatform/admin/service/
   impl/PlanServiceImpl.java:106) 将新实体转换为响应，但转换请求时忽略了
   publicId。应和素材更新一样，使用请求中的 ID 构造响应。

3. 创建计划缺少字段时，关联校验可能抛空指针异常。
   CreatePlanRequest.java:27 (src/main/java/com/example/adplatform/admin/request/
   plan/CreatePlanRequest.java:27) 直接调用 endTime.isAfter(startTime)、比较两个
   Long。@NotNull 不保证先执行并阻止这些方法执行，缺少时间或预算时可能无法返回正常
   的参数校验错误。

4. 计划部分更新可以绕过预算、时间校验。
   UpdatePlanRequest.java (src/main/java/com/example/adplatform/admin/request/
   plan/UpdatePlanRequest.java) 只检查本次同时传入的字段。数据库总预算为 100，只传
   日预算 200，仍会通过；只修改开始时间，也可能使其晚于原结束时间。Service 没有结
   合旧值检查最终结果。

5. 计划、素材的同名限制没有对应唯一索引。
   仓库 SQL 未找到两张表的 (advertiser_id, name) 联合唯一索引。若实际数据库也是如
   此，同一广告主下重复名称仍能创建或更新，现有 DuplicateKeyException 捕获不能实现
   这项限制。

6. 更新接口可以把创建时必填的字符串改为空。
   三类 UpdateRequest 中，名称等字段主要只有 @Size(max = …)，因此 ""、纯空格可以通
   过。广告主行业、联系人，以及素材图片地址等也存在同类问题。需要区分“不传”和“传入
   空白”。

7. 删除没有处理已有的关联数据。
   当前直接物理删除主表记录：删除广告主可能留下所属计划、素材；删除计划或素材可能
   留下关系表记录。仓库 SQL 也没有外键级联兜底，需要明确是有关联时禁止删除，还是同
   步清理。

8. 广告主更新没有检查更新行数。
   AdvertiserServiceImpl.java:66 (src/main/java/com/example/adplatform/admin/
   service/impl/AdvertiserServiceImpl.java:66) 查询后若记录被并发删除，
   updateById() 返回 0，接口仍然返回成功。

9. 删除不存在的计划时，错误文案写成了“素材不存在”。
   PlanServiceImpl.java:115 (src/main/java/com/example/adplatform/admin/service/
   impl/PlanServiceImpl.java:115)。