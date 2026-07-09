package com.example.adplatform.admin.controller;

import com.example.adplatform.admin.dto.CreateCampaignRequest;
import com.example.adplatform.admin.dto.UpdateCampaignRequest;
import com.example.adplatform.admin.service.CampaignService;
import com.example.adplatform.admin.vo.CampaignVO;
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
@RequestMapping("/api/admin/campaigns")
public class CampaignController {

    private final CampaignService campaignService;

    /**
     * 为启用状态的广告主创建广告计划，初始状态为草稿。
     */
    @PostMapping
    public Result<ResourceRefVO> create(@Valid @RequestBody CreateCampaignRequest request) {
        return Result.success(campaignService.create(request));
    }

    /**
     * 更新广告计划的基础信息、预算、出价和投放时间。
     */
    @PutMapping("/{id}")
    public Result<CampaignVO> update(
            @PathVariable Long id,
            @Valid @RequestBody UpdateCampaignRequest request) {
        return Result.success(campaignService.update(id, request));
    }

    /**
     * 校验广告主状态和投放时间后，将广告计划上线。
     */
    @PutMapping("/{id}/online")
    public Result<CampaignVO> online(@PathVariable Long id) {
        return Result.success(campaignService.online(id));
    }

    /**
     * 暂停在线广告计划，使其停止参与广告投放。
     */
    @PutMapping("/{id}/pause")
    public Result<CampaignVO> pause(@PathVariable Long id) {
        return Result.success(campaignService.pause(id));
    }

    /**
     * 下线广告计划，供后台管理和投放过滤使用。
     */
    @PutMapping("/{id}/offline")
    public Result<CampaignVO> offline(@PathVariable Long id) {
        return Result.success(campaignService.offline(id));
    }

    /**
     * 分页查询广告计划，支持按广告主 ID 和计划状态筛选。
     */
    @GetMapping("/page")
    public Result<PageResponse<CampaignVO>> page(
            @RequestParam(defaultValue = "1") @Min(1) long current,
            @RequestParam(defaultValue = "10") @Min(1) @Max(100) long size,
            @RequestParam(required = false) Long advertiserId,
            @RequestParam(required = false) String status) {
        return Result.success(campaignService.pageQuery(current, size, advertiserId, status));
    }
}
