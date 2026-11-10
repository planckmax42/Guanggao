package com.example.adplatform.admin.controller;

import com.example.adplatform.admin.request.plan.CreatePlanRequest;
import com.example.adplatform.admin.request.plan.PagePlanRequest;
import com.example.adplatform.admin.request.plan.UpdatePlanRequest;
import com.example.adplatform.admin.request.plan.UpdatePlanStatusRequest;
import com.example.adplatform.admin.service.PlanService;
import com.example.adplatform.admin.response.plan.PlanResponse;
import com.example.adplatform.admin.response.PageResponse;
import com.example.adplatform.common.response.ResourceRefResponse;
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

    /**
     * 为启用状态的广告主创建广告计划，初始状态为草稿。
     */
    @PostMapping
    public Result<ResourceRefResponse> create(@Valid @RequestBody CreatePlanRequest request) {
        return Result.success(planService.create(request));
    }

    /**
     * 更新广告计划的基础信息、预算、出价和投放时间。
     */
    @PutMapping
    public Result<PlanResponse> update(@Valid @RequestBody UpdatePlanRequest request) {
        return Result.success(planService.update(request.publicId(), request));
    }

    /**
     * 校验广告主状态和投放时间后，将广告计划上线。
     */
    @PutMapping("/online")
    public Result<PlanResponse> online(@Valid @RequestBody UpdatePlanStatusRequest request) {
        return Result.success(planService.online(request.publicId()));
    }

    /**
     * 暂停在线广告计划，使其停止参与广告投放。
     */
    @PutMapping("/pause")
    public Result<PlanResponse> pause(@Valid @RequestBody UpdatePlanStatusRequest request) {
        return Result.success(planService.pause(request.publicId()));
    }

    /**
     * 下线广告计划，供后台管理和投放过滤使用。
     */
    @PutMapping("/offline")
    public Result<PlanResponse> offline(@Valid @RequestBody UpdatePlanStatusRequest request) {
        return Result.success(planService.offline(request.publicId()));
    }

    /**
     * 分页查询广告计划，支持按广告主公开标识和计划状态筛选。
     */
    @PostMapping("/page")
    public Result<PageResponse<PlanResponse>> page(@Valid @RequestBody PagePlanRequest request) {
        return Result.success(planService.pageQuery(
                request.current(), request.size(), request.advertiserPublicId(), request.status()));
    }
}
