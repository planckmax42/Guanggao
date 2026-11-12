package com.example.adplatform.admin.config;

import com.example.adplatform.admin.request.advertiser.DeleteAdvertiserRequest;
import com.example.adplatform.admin.request.material.DeleteMaterialRequest;
import com.example.adplatform.admin.request.material.UpdateMaterialRequest;
import com.example.adplatform.admin.request.plan.DeletePlanRequest;
import com.example.adplatform.admin.request.rule.DeleteRuleRequest;
import com.example.adplatform.admin.request.slot.DeleteSlotRequest;
import com.example.adplatform.admin.response.advertiser.AdvertiserOperationResponse;
import com.example.adplatform.admin.response.material.MaterialOperationResponse;
import com.example.adplatform.admin.response.plan.PlanOperationResponse;
import com.example.adplatform.admin.response.plan.PlanResponse;
import com.example.adplatform.admin.response.rule.RuleOperationResponse;
import com.example.adplatform.admin.response.slot.SlotOperationResponse;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import jakarta.validation.Validation;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.assertj.core.api.Assertions.*;

class AdminIdJsonTests {
    private final ObjectMapper json = new ObjectMapper().findAndRegisterModules();
    private static final long ID = 1987654321098765432L;
    private static final List<Class<?>> DELETE_REQUESTS = List.of(DeleteAdvertiserRequest.class,
            DeleteMaterialRequest.class, DeletePlanRequest.class, DeleteRuleRequest.class, DeleteSlotRequest.class);

    @Test
    void readsLargeStringAndIntegerIdsWithoutLosingPrecision() throws Exception {
        for (Class<?> type : DELETE_REQUESTS) {
            for (String value : List.of("\"" + ID + "\"", Long.toString(ID))) {
                Object request = json.readValue("{\"id\":" + value + "}", type);
                assertThat(type.getMethod("id").invoke(request)).isEqualTo(ID);
            }
        }
    }

    @Test
    void rejectsFractionalBooleanMalformedAndOverflowIds() {
        for (Class<?> type : DELETE_REQUESTS) {
            for (String value : List.of("1.5", "1.0", "1e3", "true", "[]", "{}", "\"\"", "\" \"",
                    "\"adv_123\"", "\"1.5\"", "9223372036854775808", "\"9223372036854775808\"")) {
                assertThatThrownBy(() -> json.readValue("{\"id\":" + value + "}", type))
                        .as(type.getSimpleName() + " " + value).isInstanceOf(JsonProcessingException.class);
            }
        }
    }

    @Test
    void requiresPositiveTargetIdAndAllowsOmittedOptionalAssociation() throws Exception {
        try (var factory = Validation.buildDefaultValidatorFactory()) {
            for (Class<?> type : DELETE_REQUESTS) {
                for (String body : List.of("{}", "{\"id\":null}", "{\"id\":0}", "{\"id\":-1}")) {
                    assertThat(factory.getValidator().validate(json.readValue(body, type)))
                            .extracting(v -> v.getPropertyPath().toString()).containsExactly("id");
                }
            }
            var request = json.readValue("{\"id\":\"" + ID + "\",\"name\":\"新名称\"}", UpdateMaterialRequest.class);
            assertThat(request.slotId()).isNull();
            assertThat(factory.getValidator().validate(request)).isEmpty();
        }
    }

    @Test
    void returnsIdsAsStringsButKeepsMoneyNumeric() throws Exception {
        for (Object response : List.of(new AdvertiserOperationResponse(ID), new MaterialOperationResponse(ID),
                new PlanOperationResponse(ID), new RuleOperationResponse(ID), new SlotOperationResponse(ID))) {
            var node = json.readTree(json.writeValueAsString(response));
            assertThat(node.get("id").isTextual()).isTrue();
            assertThat(node.get("id").textValue()).isEqualTo(Long.toString(ID));
            assertThat(node.has("publicId")).isFalse();
        }
        var response = new PlanResponse(ID, ID + 1, "计划", 100L, 10L, 1L, "CPC", null, null, "OFFLINE");
        var node = json.readTree(json.writeValueAsString(response));
        assertThat(node.get("advertiserId").isTextual()).isTrue();
        assertThat(node.get("budgetTotal").isIntegralNumber()).isTrue();
    }
}
