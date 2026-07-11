package com.example.adplatform.admin.controller;

import com.example.adplatform.admin.dto.CreateRuleRequest;
import com.example.adplatform.admin.service.RuleService;
import com.example.adplatform.admin.vo.RuleVO;
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
@RequestMapping("/api/admin/rules")
public class RuleController {

    private final RuleService ruleService;

    /**
     * 创建或替换广告计划的定向规则。
     */
    @PostMapping
    public Result<ResourceRefVO> createOrUpdate(@Valid @RequestBody CreateRuleRequest request) {
        return Result.success(ruleService.createOrUpdate(request));
    }

    /**
     * 查询指定广告计划已配置的定向规则。
     */
    @GetMapping("/{planId}")
    public Result<RuleVO> getByPlanId(@PathVariable Long planId) {
        return Result.success(ruleService.getByPlanId(planId));
    }
}
