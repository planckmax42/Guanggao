package com.example.adplatform.admin.converter;

import com.example.adplatform.admin.entity.RuleEntity;
import com.example.adplatform.admin.request.rule.CreateRuleRequest;
import com.example.adplatform.admin.request.rule.UpdateRuleRequest;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.test.context.junit.jupiter.SpringJUnitConfig;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

@SpringJUnitConfig(classes = {RuleConverterImpl.class})
class RuleConverterTests {

    @Autowired
    private RuleConverter converter;

    @Test
    void shouldConvertRuleAndMapAdvertiserPublicId() {
        CreateRuleRequest request = new CreateRuleRequest(
                "adv_1", "规则一", List.of("ANDROID"), null, 18, 45);
        RuleEntity entity = converter.createRequestToEntity(request);
        entity.initializePublicId("rule_1");
        entity.setAdvertiserPublicId("adv_1");

        var response = converter.entityToQueryResponse(entity);

        assertThat(response.name()).isEqualTo("规则一");
        assertThat(entity.getName()).isEqualTo("规则一");
        assertThat(response.advertiserPublicId()).isEqualTo("adv_1");
        assertThat(response.deviceTypes()).containsExactly("ANDROID");
        assertThat(entity.getAdvertiserPublicId()).isEqualTo("adv_1");
        assertThat(converter.entityToOperationResponse(entity).publicId()).isEqualTo("rule_1");

        var update = converter.updateRequestToEntity(new UpdateRuleRequest("rule_1", "规则二",
                request.deviceTypes(), request.gender(), request.ageMin(), request.ageMax()));
        assertThat(update.getName()).isEqualTo("规则二");
        assertThat(update.getAdvertiserPublicId()).isNull();
        assertThat(update.getPublicId()).isNull();
        assertThat(update.getDeviceTypes()).isEqualTo(entity.getDeviceTypes());
        assertThat(converter.entityToQueryResponse(entity)).isEqualTo(response);
    }

    @Test
    void shouldPreserveEmptyTargetingBehavior() {
        RuleEntity entity = converter.createRequestToEntity(new CreateRuleRequest(
                "adv_1", "规则一", List.of(), null, null, null));

        assertThat(entity.getDeviceTypes()).isEmpty();
        assertThat(converter.entityToQueryResponse(entity).deviceTypes()).isEmpty();
    }

    @Test
    void shouldReturnEmptyDevicesForStoredNull() {
        RuleEntity entity = new RuleEntity();

        assertThat(converter.entityToQueryResponse(entity).deviceTypes()).isEmpty();
    }
}
