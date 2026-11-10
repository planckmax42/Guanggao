package com.example.adplatform.admin.converter;

import com.example.adplatform.admin.entity.RuleEntity;
import com.example.adplatform.admin.request.rule.CreateRuleRequest;
import com.example.adplatform.common.exception.BusinessException;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.test.context.junit.jupiter.SpringJUnitConfig;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

@SpringJUnitConfig(classes = {RuleConverterImpl.class, RuleJsonConverter.class, ObjectMapper.class})
class RuleConverterTests {

    @Autowired
    private RuleConverter converter;

    @Test
    void shouldConvertRuleAndMapPlanPublicId() {
        CreateRuleRequest request = new CreateRuleRequest(
                "plan_1", List.of("北京"), List.of("ANDROID"), null, 18, 45, List.of("shopping"));
        RuleEntity entity = converter.toEntity(request);
        entity.initializePublicId("rule_1");
        entity.setPlanId(10L);

        var response = converter.toResponse(entity, request.planPublicId());

        assertThat(response.planPublicId()).isEqualTo("plan_1");
        assertThat(response.regions()).containsExactly("北京");
        assertThat(response.deviceTypes()).containsExactly("ANDROID");
        assertThat(response.userTags()).containsExactly("shopping");
        assertThat(converter.toRef(entity, "plan_1").bizKey()).isEqualTo("plan_1");
        assertThat(converter.toRef(entity, "plan_1").publicId()).isEqualTo("rule_1");

        converter.updateEntity(request, entity);
        converter.updateEntity(request, entity);

        assertThat(converter.toResponse(entity, "plan_1")).isEqualTo(response);
        assertThat(entity.getPlanId()).isEqualTo(10L);
    }

    @Test
    void shouldPreserveEmptyTargetingBehavior() {
        RuleEntity entity = converter.toEntity(new CreateRuleRequest(
                "plan_1", null, List.of(), null, null, null, null));

        assertThat(entity.getRegion()).isNull();
        assertThat(entity.getDeviceType()).isNull();
        assertThat(converter.toResponse(entity, "plan_1").regions()).isEmpty();
        assertThat(converter.toResponse(entity, "plan_1").deviceTypes()).isEmpty();
    }

    @Test
    void shouldRejectInvalidStoredJson() {
        RuleEntity entity = new RuleEntity();
        entity.setRegion("invalid-json");

        assertThatThrownBy(() -> converter.toResponse(entity, "plan_1"))
                .isInstanceOf(BusinessException.class)
                .hasMessage("定向规则解析失败");
    }
}
