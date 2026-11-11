package com.example.adplatform.admin.converter;

import com.example.adplatform.admin.entity.RuleEntity;
import com.example.adplatform.admin.request.rule.CreateRuleRequest;
import com.example.adplatform.admin.request.rule.UpdateRuleRequest;
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
        RuleEntity entity = converter.createRequestToEntity(request);
        entity.initializePublicId("rule_1");
        entity.setPlanPublicId("plan_1");

        var response = converter.entityToQueryResponse(entity);

        assertThat(response.planPublicId()).isEqualTo("plan_1");
        assertThat(response.regions()).containsExactly("北京");
        assertThat(response.deviceTypes()).containsExactly("ANDROID");
        assertThat(response.userTags()).containsExactly("shopping");
        assertThat(entity.getPlanPublicId()).isEqualTo("plan_1");
        assertThat(converter.entityToOperationResponse(entity).publicId()).isEqualTo("rule_1");

        converter.updateRequestToEntity(new UpdateRuleRequest("rule_1", request.planPublicId(), request.regions(), request.deviceTypes(), request.gender(), request.ageMin(), request.ageMax(), request.userTags()), entity);
        converter.updateRequestToEntity(new UpdateRuleRequest("rule_1", request.planPublicId(), request.regions(), request.deviceTypes(), request.gender(), request.ageMin(), request.ageMax(), request.userTags()), entity);

        assertThat(converter.entityToQueryResponse(entity)).isEqualTo(response);
        assertThat(entity.getPlanPublicId()).isEqualTo("plan_1");
    }

    @Test
    void shouldPreserveEmptyTargetingBehavior() {
        RuleEntity entity = converter.createRequestToEntity(new CreateRuleRequest(
                "plan_1", null, List.of(), null, null, null, null));

        assertThat(entity.getRegion()).isNull();
        assertThat(entity.getDeviceType()).isNull();
        assertThat(converter.entityToQueryResponse(entity).regions()).isEmpty();
        assertThat(converter.entityToQueryResponse(entity).deviceTypes()).isEmpty();
    }

    @Test
    void shouldRejectInvalidStoredJson() {
        RuleEntity entity = new RuleEntity();
        entity.setRegion("invalid-json");

        assertThatThrownBy(() -> converter.entityToQueryResponse(entity))
                .isInstanceOf(BusinessException.class)
                .hasMessage("定向规则解析失败");
    }
}
