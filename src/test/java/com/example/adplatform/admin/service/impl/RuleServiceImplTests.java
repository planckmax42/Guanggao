package com.example.adplatform.admin.service.impl;

import com.baomidou.mybatisplus.core.conditions.update.LambdaUpdateWrapper;
import com.baomidou.mybatisplus.core.metadata.TableInfoHelper;
import com.example.adplatform.admin.converter.*;
import com.example.adplatform.admin.entity.RuleEntity;
import com.example.adplatform.admin.mapper.*;
import com.example.adplatform.admin.request.rule.*;
import com.example.adplatform.search.outbox.message.ConfigAggregateType;
import com.example.adplatform.search.outbox.service.SearchOutboxService;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.apache.ibatis.builder.MapperBuilderAssistant;
import com.baomidou.mybatisplus.core.MybatisConfiguration;
import org.junit.jupiter.api.Test;
import org.mapstruct.factory.Mappers;
import org.springframework.test.util.ReflectionTestUtils;

import static org.assertj.core.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

class RuleServiceImplTests {
    private final RuleMapper mapper = mock(RuleMapper.class);
    private final PlanMapper plans = mock(PlanMapper.class);
    private final SearchOutboxService outbox = mock(SearchOutboxService.class);
    private final RuleConverter converter = Mappers.getMapper(RuleConverter.class);
    private final RuleServiceImpl service = new RuleServiceImpl(mapper, plans, converter, outbox);

    RuleServiceImplTests() {
        ReflectionTestUtils.setField(converter, "ruleJsonConverter", new RuleJsonConverter(new ObjectMapper()));
        TableInfoHelper.initTableInfo(new MapperBuilderAssistant(new MybatisConfiguration(), ""), RuleEntity.class);
    }

    @Test
    void shouldCreateWithPlanPublicId() {
        when(plans.exists(any())).thenReturn(true);
        var response = service.create(new CreateRuleRequest("plan_1", null, null, null, null, null, null));
        assertThat(response.publicId()).startsWith("rule_");
        verify(mapper).insert(argThat((RuleEntity rule) -> rule.getPlanPublicId().equals("plan_1")));
        verify(outbox).appendConfigChange(ConfigAggregateType.RULE, response.publicId());
    }

    @Test
    void shouldClearTargetingAndKeepPlanReferenceOnRepeatedUpdate() {
        RuleEntity entity = rule();
        entity.setGender("MALE");
        when(mapper.selectOne(any())).thenReturn(entity);
        doAnswer(call -> {
            LambdaUpdateWrapper<RuleEntity> update = call.getArgument(1);
            assertThat(update.getSqlSet()).contains("gender=", "age_min=", "device_type=").doesNotContain("plan_id=");
            assertThat(update.getParamNameValuePairs()).containsValue(null);
            return 1;
        }).when(mapper).update(any(RuleEntity.class), any());
        var request = new UpdateRuleRequest("rule_1", null, null, null, null, null, null);
        service.update(request);
        service.update(request);
        assertThat(entity.getGender()).isNull();
        assertThat(entity.getPlanPublicId()).isEqualTo("plan_1");
        verify(mapper, times(2)).update(any(RuleEntity.class), any());
    }

    @Test
    void shouldRebuildPlanAfterDeletingRule() {
        when(mapper.selectOne(any())).thenReturn(rule(), null);
        when(mapper.deleteById(1L)).thenReturn(1);
        service.delete(new DeleteRuleRequest("rule_1"));
        verify(outbox).appendConfigChange(ConfigAggregateType.PLAN, "plan_1");
        assertThatThrownBy(() -> service.delete(new DeleteRuleRequest("rule_1"))).hasMessage("定向规则不存在");
    }

    private RuleEntity rule() {
        RuleEntity rule = new RuleEntity();
        rule.setId(1L);
        rule.initializePublicId("rule_1");
        rule.setPlanPublicId("plan_1");
        return rule;
    }
}
