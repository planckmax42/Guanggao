package com.example.adplatform.admin.service;

import com.example.adplatform.admin.dto.CreateTargetingRuleRequest;
import com.example.adplatform.admin.vo.TargetingRuleVO;
import com.example.adplatform.common.response.ResourceRefVO;

public interface TargetingRuleService {

    ResourceRefVO createOrUpdate(CreateTargetingRuleRequest request);

    TargetingRuleVO getByCampaignId(Long campaignId);
}
