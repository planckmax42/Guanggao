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
    void shouldConvertRuleAndMapAdvertiserId() {
        CreateRuleRequest request = new CreateRuleRequest(
                1L, "规则一", List.of("ANDROID"), null, 18, 45);
        RuleEntity entity = converter.createRequestToEntity(request);
        entity.setId(1L);
        entity.setAdvertiserId(1L);

        var response = converter.entityToQueryResponse(entity);

        assertThat(response.name()).isEqualTo("规则一");
        assertThat(entity.getName()).isEqualTo("规则一");
        assertThat(response.advertiserId()).isEqualTo(1L);
        assertThat(response.deviceTypes()).containsExactly("ANDROID");
        assertThat(entity.getAdvertiserId()).isEqualTo(1L);
        assertThat(converter.entityToOperationResponse(entity).id()).isEqualTo(1L);

        var update = converter.updateRequestToEntity(new UpdateRuleRequest(1L, "规则二",
                request.deviceTypes(), request.gender(), request.ageMin(), request.ageMax()));
        assertThat(update.getName()).isEqualTo("规则二");
        assertThat(update.getAdvertiserId()).isNull();
        assertThat(update.getId()).isNull();
        assertThat(update.getDeviceTypes()).isEqualTo(entity.getDeviceTypes());
        assertThat(converter.entityToQueryResponse(entity)).isEqualTo(response);
    }

    @Test
    void shouldPreserveEmptyTargetingBehavior() {
        RuleEntity entity = converter.createRequestToEntity(new CreateRuleRequest(
                1L, "规则一", List.of(), null, null, null));

        assertThat(entity.getDeviceTypes()).isEmpty();
        assertThat(converter.entityToQueryResponse(entity).deviceTypes()).isEmpty();
    }

    @Test
    void shouldReturnEmptyDevicesForStoredNull() {
        RuleEntity entity = new RuleEntity();

        assertThat(converter.entityToQueryResponse(entity).deviceTypes()).isEmpty();
    }
}
