package com.example.adplatform.admin.service;

import com.example.adplatform.admin.request.rule.CreateRuleRequest;
import com.example.adplatform.admin.request.rule.UpdateRuleRequest;
import com.example.adplatform.admin.request.rule.DeleteRuleRequest;
import com.example.adplatform.admin.request.rule.PageRuleRequest;
import com.example.adplatform.admin.response.PageResponse;
import com.example.adplatform.admin.response.rule.RuleOperationResponse;
import com.example.adplatform.admin.response.rule.RuleResponse;

public interface RuleService {

    RuleOperationResponse create(CreateRuleRequest request);

    PageResponse<RuleResponse> pageQuery(PageRuleRequest request);

    RuleOperationResponse update(UpdateRuleRequest request);

    RuleOperationResponse delete(DeleteRuleRequest request);
}
