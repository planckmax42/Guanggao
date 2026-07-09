package com.example.adplatform.admin.controller;

import com.example.adplatform.admin.dto.CreateTargetingRuleRequest;
import com.example.adplatform.admin.service.TargetingRuleService;
import com.example.adplatform.admin.vo.TargetingRuleVO;
import com.example.adplatform.common.response.ResourceRefVO;
import com.example.adplatform.common.response.Result;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RequiredArgsConstructor
@RestController
@RequestMapping("/api/admin/targeting-rules")
public class TargetingRuleController {

    private final TargetingRuleService targetingRuleService;

    /**
     * 创建或替换广告计划的定向规则。
     */
    @PostMapping
    public Result<ResourceRefVO> createOrUpdate(@Valid @RequestBody CreateTargetingRuleRequest request) {
        return Result.success(targetingRuleService.createOrUpdate(request));
    }

    /**
     * 查询指定广告计划已配置的定向规则。
     */
    @GetMapping("/{campaignId}")
    public Result<TargetingRuleVO> getByCampaignId(@PathVariable Long campaignId) {
        return Result.success(targetingRuleService.getByCampaignId(campaignId));
    }
}
