package com.example.adplatform.admin.service;

import com.example.adplatform.admin.request.CreateRuleRequest;
import com.example.adplatform.admin.response.RuleResponse;
import com.example.adplatform.common.response.ResourceRefResponse;

public interface RuleService {

    ResourceRefResponse createOrUpdate(CreateRuleRequest request);

    RuleResponse getByPlanPublicId(String planPublicId);
}
