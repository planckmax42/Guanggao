package com.example.adplatform.admin.controller;

import com.example.adplatform.admin.request.rule.CreateRuleRequest;
import com.example.adplatform.admin.request.rule.UpdateRuleRequest;
import com.example.adplatform.admin.request.rule.DeleteRuleRequest;
import com.example.adplatform.admin.request.rule.PageRuleRequest;
import com.example.adplatform.admin.response.PageResponse;
import com.example.adplatform.admin.response.rule.RuleOperationResponse;
import com.example.adplatform.admin.response.rule.RuleResponse;
import com.example.adplatform.admin.service.RuleService;
import com.example.adplatform.common.response.Result;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RequiredArgsConstructor
@Validated
@RestController
@RequestMapping("/api/advertiser/rules")
public class RuleController {

    private final RuleService ruleService;

    @PostMapping("/create")
    public Result<RuleOperationResponse> create(@Valid @RequestBody CreateRuleRequest request) {
        return Result.success(ruleService.create(request));
    }

    @PostMapping("/page")
    public Result<PageResponse<RuleResponse>> page(@Valid @RequestBody PageRuleRequest request) {
        return Result.success(ruleService.pageQuery(request));
    }

    @PutMapping("/update")
    public Result<RuleOperationResponse> update(@Valid @RequestBody UpdateRuleRequest request) {
        return Result.success(ruleService.update(request));
    }

    @DeleteMapping("/delete")
    public Result<RuleOperationResponse> delete(@Valid @RequestBody DeleteRuleRequest request) {
        return Result.success(ruleService.delete(request));
    }
}
