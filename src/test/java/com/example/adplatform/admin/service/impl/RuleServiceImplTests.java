package com.example.adplatform.admin.service.impl;

import com.example.adplatform.admin.converter.RuleConverter;
import com.example.adplatform.admin.entity.PlanEntity;
import com.example.adplatform.admin.entity.RuleEntity;
import com.example.adplatform.admin.mapper.PlanMapper;
import com.example.adplatform.admin.mapper.RuleMapper;
import com.example.adplatform.admin.request.CreateRuleRequest;
import com.example.adplatform.search.outbox.message.ConfigAggregateType;
import com.example.adplatform.search.outbox.service.SearchOutboxService;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class RuleServiceImplTests {

    @Test
    void shouldPublishRulePublicIdForRuleAggregate() {
        RuleMapper ruleMapper = mock(RuleMapper.class);
        PlanMapper planMapper = mock(PlanMapper.class);
        RuleConverter ruleConverter = mock(RuleConverter.class);
        SearchOutboxService searchOutboxService = mock(SearchOutboxService.class);
        RuleServiceImpl service = new RuleServiceImpl(
                ruleMapper, planMapper, ruleConverter, searchOutboxService);

        PlanEntity plan = new PlanEntity();
        plan.setId(10L);
        RuleEntity rule = new RuleEntity();
        rule.setId(20L);
        rule.initializePublicId("rule_00000000000000000000000000000001");
        rule.setPlanId(plan.getId());
        when(planMapper.selectOne(any())).thenReturn(plan);
        when(ruleMapper.selectOne(any())).thenReturn(rule);
        CreateRuleRequest request = new CreateRuleRequest(
                "plan_00000000000000000000000000000001",
                List.of("BEIJING"),
                List.of("ANDROID"),
                null,
                18,
                45,
                List.of("shopping"));

        service.createOrUpdate(request);

        verify(searchOutboxService).appendConfigChange(
                ConfigAggregateType.RULE,
                "rule_00000000000000000000000000000001");
    }
}
