package com.example.adplatform.admin.controller;

import com.example.adplatform.admin.dto.CreatePlanRequest;
import com.example.adplatform.admin.dto.UpdatePlanRequest;
import com.example.adplatform.admin.service.PlanService;
import com.example.adplatform.admin.vo.PlanVO;
import com.example.adplatform.common.response.PageResponse;
import com.example.adplatform.common.response.ResourceRefVO;
import com.example.adplatform.common.response.Result;
import jakarta.validation.Valid;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
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

@Validated
@RequiredArgsConstructor
@RestController
@RequestMapping("/api/admin/plans")
public class PlanController {

    private final PlanService planService;

    /**
     * 为启用状态的广告主创建广告计划，初始状态为草稿。
     */
    @PostMapping
    public Result<ResourceRefVO> create(@Valid @RequestBody CreatePlanRequest request) {
        return Result.success(planService.create(request));
    }

    /**
     * 更新广告计划的基础信息、预算、出价和投放时间。
     */
    @PutMapping("/{id}")
    public Result<PlanVO> update(
            @PathVariable Long id,
            @Valid @RequestBody UpdatePlanRequest request) {
        return Result.success(planService.update(id, request));
    }

    /**
     * 校验广告主状态和投放时间后，将广告计划上线。
     */
    @PutMapping("/{id}/online")
    public Result<PlanVO> online(@PathVariable Long id) {
        return Result.success(planService.online(id));
    }

    /**
     * 暂停在线广告计划，使其停止参与广告投放。
     */
    @PutMapping("/{id}/pause")
    public Result<PlanVO> pause(@PathVariable Long id) {
        return Result.success(planService.pause(id));
    }

    /**
     * 下线广告计划，供后台管理和投放过滤使用。
     */
    @PutMapping("/{id}/offline")
    public Result<PlanVO> offline(@PathVariable Long id) {
        return Result.success(planService.offline(id));
    }

    /**
     * 分页查询广告计划，支持按广告主 ID 和计划状态筛选。
     */
    @GetMapping("/page")
    public Result<PageResponse<PlanVO>> page(
            @RequestParam(defaultValue = "1") @Min(1) long current,
            @RequestParam(defaultValue = "10") @Min(1) @Max(100) long size,
            @RequestParam(required = false) Long userId,
            @RequestParam(required = false) String status) {
        return Result.success(planService.pageQuery(current, size, userId, status));
    }
}
