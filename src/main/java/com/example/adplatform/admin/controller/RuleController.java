package com.example.adplatform.admin.controller;

import com.example.adplatform.admin.request.rule.CreateRuleRequest;
import com.example.adplatform.admin.request.rule.QueryRuleRequest;
import com.example.adplatform.admin.service.RuleService;
import com.example.adplatform.admin.response.rule.RuleResponse;
import com.example.adplatform.common.response.ResourceRefResponse;
import com.example.adplatform.common.response.Result;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RequiredArgsConstructor
@Validated
@RestController
@RequestMapping("/api/advertiser/rules")
public class RuleController {

    private final RuleService ruleService;

    /**
     * 创建或替换广告计划的定向规则。
     */
    @PostMapping
    public Result<ResourceRefResponse> createOrUpdate(@Valid @RequestBody CreateRuleRequest request) {
        return Result.success(ruleService.createOrUpdate(request));
    }

    /**
     * 查询指定广告计划已配置的定向规则。
     */
    @PostMapping("/by-plan")
    public Result<RuleResponse> getByPlanPublicId(@Valid @RequestBody QueryRuleRequest request) {
        return Result.success(ruleService.getByPlanPublicId(request.planPublicId()));
    }
}
