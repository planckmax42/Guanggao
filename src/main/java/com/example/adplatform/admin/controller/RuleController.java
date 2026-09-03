package com.example.adplatform.admin.controller;

import com.example.adplatform.admin.request.CreateRuleRequest;
import com.example.adplatform.admin.service.RuleService;
import com.example.adplatform.admin.response.RuleResponse;
import com.example.adplatform.common.response.ResourceRefResponse;
import com.example.adplatform.common.response.Result;
import jakarta.validation.Valid;
import jakarta.validation.constraints.Pattern;
import lombok.RequiredArgsConstructor;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import static com.example.adplatform.common.id.PublicIdGenerator.PLAN_PATTERN;

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
    @GetMapping("/by-plan/{planPublicId}")
    public Result<RuleResponse> getByPlanPublicId(
            @PathVariable @Pattern(regexp = PLAN_PATTERN) String planPublicId) {
        return Result.success(ruleService.getByPlanPublicId(planPublicId));
    }
}
