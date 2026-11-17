package com.example.adplatform.admin.controller;

import org.springframework.web.bind.annotation.DeleteMapping;
import com.example.adplatform.admin.request.plan.DeletePlanRequest;
import com.example.adplatform.admin.request.plan.CreatePlanRequest;
import com.example.adplatform.admin.request.plan.PagePlanRequest;
import com.example.adplatform.admin.request.plan.UpdatePlanRequest;
import com.example.adplatform.admin.request.plan.UpdatePlanStatusRequest;
import com.example.adplatform.admin.response.plan.PlanOperationResponse;
import com.example.adplatform.admin.service.PlanService;
import com.example.adplatform.admin.response.plan.PlanResponse;
import com.example.adplatform.admin.response.PageResponse;
import com.example.adplatform.common.response.Result;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@Validated
@RequiredArgsConstructor
@RestController
@RequestMapping("/api/advertiser/plans")
public class PlanController {

    private final PlanService planService;

    @PostMapping("/create")
    public Result<PlanOperationResponse> create(@Valid @RequestBody CreatePlanRequest request) {
        return Result.success(planService.create(request));
    }

    @PostMapping("/page")
    public Result<PageResponse<PlanResponse>> page(@Valid @RequestBody PagePlanRequest request) {
        return Result.success(planService.pageQuery(request));
    }

    @PutMapping("/update")
    public Result<PlanOperationResponse> update(@Valid @RequestBody UpdatePlanRequest request) {
        return Result.success(planService.update(request));
    }

    @DeleteMapping("/delete")
    public Result<PlanOperationResponse> delete(@Valid @RequestBody DeletePlanRequest request) {
        return Result.success(planService.delete(request));
    }









    @PutMapping("/online")
    public Result<PlanResponse> online(@Valid @RequestBody UpdatePlanStatusRequest request) {
        return Result.success(planService.online(request));
    }

    @PutMapping("/pause")
    public Result<PlanResponse> pause(@Valid @RequestBody UpdatePlanStatusRequest request) {
        return Result.success(planService.pause(request));
    }

    @PutMapping("/offline")
    public Result<PlanResponse> offline(@Valid @RequestBody UpdatePlanStatusRequest request) {
        return Result.success(planService.offline(request));
    }
}
//关于删除的逻辑我是这样想的 当删除plan  mysql层面不同步删除与之相关的素材  es层面删除所有与之相关的document     当删除material   mysql层面必须不存在与之相关的plan才可以删除   es层面当前应该是不存在与之相关的plan      当删除slot  mysql层面不应存在与之相关的material 才可以删除  此时es层面应该是不村子与之相关的material

//这套业务规则合理。 素材可以独立于计划保留，广告位则必须先解除素材占用。需要补充的是：MySQL 没有关联，不代表此时 ES 已经清理完毕。
//建议明确成下面这样：
//删除对象	MySQL 处理	ES 处理
//plan	删除计划及其 plan_material_relation 记录，保留素材	按 planId 删除所有候选文档
//material	存在任何计划关联则拒绝，否则删除素材	按 materialId 清理残留文档
//slot	存在任何关联素材则拒绝，否则删除广告位	按 slotId 清理残留文档，同时清除广告位缓存
//
//
//删除计划时，关联记录也要删。
//例如素材 M 同时属于计划 A、B，删除 A 后：
//MySQL：删除 A、删除 A—M 关联；保留 M 和 B—M 关联
//ES：删除 planId=A 的文档；保留 planId=B 的文档
//之后删除 M，仍应因为 B 在引用它而失败。这里判断的是是否存在关联，不应只检查计划是否上线。
//素材和广告位删除成功后，ES 理论上应该没有相关文档，但仍建议发送清理事件。
//你的同步链路是异步的，可能出现：
//MySQL 已删除计划
//→ ES 删除消息尚未消费
//→ 用户继续删除素材
//此时 ES 仍可能有旧文档。按 ID 再清理一次是幂等操作，没有匹配文档也可以正常完成。还要保证晚到的创建或更新消息不会把已删除的候选重新写回来。
//数据库约束可以这样配置，避免“检查完关联后，另一请求又新增关联”的并发问题：
//plan_material_relation.plan_id → plan.id
//ON DELETE CASCADE    删除计划时，仅级联删除关联记录
//
//plan_material_relation.material_id → material.id
//ON DELETE RESTRICT   素材被计划引用时禁止删除
//
//material.slot_id → slot.id
//ON DELETE RESTRICT   广告位被素材引用时禁止删除
//从当前源码看，三个删除方法主要依赖外键异常拦截，但已有的计划—素材建表脚本没有声明外键，部分同步调用也被注释了，所以现在不能认为这些规则已经生效。
//落地时建议让 MySQL 删除与 Outbox 删除事件在同一事务提交，再异步清理 ES；删除计划后的同步窗口，则复用停投标记阻止旧候选继续投放。