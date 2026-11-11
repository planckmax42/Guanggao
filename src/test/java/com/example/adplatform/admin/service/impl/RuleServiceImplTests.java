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
    void shouldRejectZeroUpdateCountBeforePublishingChanges() {
        when(mapper.selectOne(any())).thenReturn(rule());
        when(mapper.update(any(RuleEntity.class), any())).thenReturn(0);
        assertThatThrownBy(() -> service.update(new UpdateRuleRequest("rule_1", null, null, null, "FEMALE", null, null, null))).hasMessage("定向规则不存在");
        verifyNoInteractions(outbox);
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
    void shouldClearSuppliedListAndPreserveOmittedFieldsOnRepeatedUpdate() {
        RuleEntity entity = rule();
        entity.setGender("MALE");
        when(plans.exists(any())).thenReturn(true);
        when(mapper.selectOne(any())).thenReturn(entity);
        doAnswer(call -> {
            LambdaUpdateWrapper<RuleEntity> update = call.getArgument(1);
            assertThat(update.getSqlSet()).contains("region=", "plan_id=").doesNotContain("gender=", "age_min=", "device_type=");
            assertThat(update.getParamNameValuePairs()).containsValue(null);
            return 1;
        }).when(mapper).update(any(RuleEntity.class), any());
        var request = new UpdateRuleRequest("rule_1", "plan_1", java.util.List.of(), null, null, null, null, null);
        service.update(request);
        service.update(request);
        assertThat(entity.getGender()).isEqualTo("MALE");
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

    @Test
    void shouldMoveRuleAndRebuildOldAndNewPlan() {
        RuleEntity entity = rule();
        when(mapper.selectOne(any())).thenReturn(entity);
        when(plans.exists(any())).thenReturn(true);
        when(mapper.update(any(RuleEntity.class), any())).thenReturn(1);
        service.update(new UpdateRuleRequest("rule_1", "plan_2", null, null, null, null, null, null));
        assertThat(entity.getPlanPublicId()).isEqualTo("plan_2");
        verify(mapper).update(any(RuleEntity.class), argThat(wrapper -> {
            LambdaUpdateWrapper<RuleEntity> update = (LambdaUpdateWrapper<RuleEntity>) wrapper;
            return update.getSqlSet().contains("plan_id=")
                    && update.getParamNameValuePairs().containsValue("plan_2");
        }));
        verify(outbox).appendConfigChange(ConfigAggregateType.PLAN, "plan_1");
        verify(outbox).appendConfigChange(ConfigAggregateType.RULE, "rule_1");
    }

    @Test
    void shouldRejectMissingOrOccupiedTargetPlan() {
        when(mapper.selectOne(any())).thenReturn(rule());
        var request = new UpdateRuleRequest("rule_1", "plan_2", null, null, null, null, null, null);
        assertThatThrownBy(() -> service.update(request)).hasMessage("广告计划不存在");
        when(plans.exists(any())).thenReturn(true);
        when(mapper.exists(any())).thenReturn(true);
        assertThatThrownBy(() -> service.update(request)).hasMessage("目标广告计划已存在定向规则");
        verify(mapper, never()).update(any(RuleEntity.class), any());
        verifyNoInteractions(outbox);
    }

    private RuleEntity rule() {
        RuleEntity rule = new RuleEntity();
        rule.setId(1L);
        rule.initializePublicId("rule_1");
        rule.setPlanPublicId("plan_1");
        return rule;
    }
}
