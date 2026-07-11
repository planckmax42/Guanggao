package com.example.adplatform.admin.service;

import com.example.adplatform.admin.dto.CreateRuleRequest;
import com.example.adplatform.admin.vo.RuleVO;
import com.example.adplatform.common.response.ResourceRefVO;

public interface RuleService {

    ResourceRefVO createOrUpdate(CreateRuleRequest request);

    RuleVO getByPlanId(Long planId);
}
