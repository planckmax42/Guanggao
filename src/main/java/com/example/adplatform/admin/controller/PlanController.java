package com.example.adplatform.admin.controller;

import com.example.adplatform.admin.request.CreatePlanRequest;
import com.example.adplatform.admin.request.UpdatePlanRequest;
import com.example.adplatform.admin.service.PlanService;
import com.example.adplatform.admin.response.PlanResponse;
import com.example.adplatform.common.response.PageResponse;
import com.example.adplatform.common.response.ResourceRefResponse;
import com.example.adplatform.common.response.Result;
import jakarta.validation.Valid;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.Pattern;
import lombok.RequiredArgsConstructor;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import static com.example.adplatform.common.id.PublicIdGenerator.ADVERTISER_PATTERN;
import static com.example.adplatform.common.id.PublicIdGenerator.PLAN_PATTERN;

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
    @PutMapping("/{publicId}")
    public Result<PlanResponse> update(
            @PathVariable @Pattern(regexp = PLAN_PATTERN) String publicId,
            @Valid @RequestBody UpdatePlanRequest request) {
        return Result.success(planService.update(publicId, request));
    }

    /**
     * 校验广告主状态和投放时间后，将广告计划上线。
     */
    @PutMapping("/{publicId}/online")
    public Result<PlanResponse> online(
            @PathVariable @Pattern(regexp = PLAN_PATTERN) String publicId) {
        return Result.success(planService.online(publicId));
    }

    /**
     * 暂停在线广告计划，使其停止参与广告投放。
     */
    @PutMapping("/{publicId}/pause")
    public Result<PlanResponse> pause(
            @PathVariable @Pattern(regexp = PLAN_PATTERN) String publicId) {
        return Result.success(planService.pause(publicId));
    }

    /**
     * 下线广告计划，供后台管理和投放过滤使用。
     */
    @PutMapping("/{publicId}/offline")
    public Result<PlanResponse> offline(
            @PathVariable @Pattern(regexp = PLAN_PATTERN) String publicId) {
        return Result.success(planService.offline(publicId));
    }

    /**
     * 分页查询广告计划，支持按广告主公开标识和计划状态筛选。
     */
    @GetMapping("/page")
    public Result<PageResponse<PlanResponse>> page(
            @RequestParam(defaultValue = "1") @Min(1) long current,
            @RequestParam(defaultValue = "10") @Min(1) @Max(100) long size,
            @RequestParam(required = false) @Pattern(regexp = ADVERTISER_PATTERN) String advertiserPublicId,
            @RequestParam(required = false) String status) {
        return Result.success(planService.pageQuery(current, size, advertiserPublicId, status));
    }
}
