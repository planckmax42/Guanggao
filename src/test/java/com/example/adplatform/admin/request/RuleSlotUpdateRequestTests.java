package com.example.adplatform.admin.request;

import com.example.adplatform.admin.request.rule.UpdateRuleRequest;
import com.example.adplatform.admin.request.rule.CreateRuleRequest;
import com.example.adplatform.admin.request.slot.UpdateSlotRequest;
import com.example.adplatform.admin.response.rule.RuleResponse;
import com.fasterxml.jackson.databind.ObjectMapper;
import jakarta.validation.Validation;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

class RuleSlotUpdateRequestTests {

    @Test
    void validatesOptionalRuleNameAndAcceptsRenameOnly() {
        Long id = 1L;
        try (var factory = Validation.buildDefaultValidatorFactory()) {
            var validator = factory.getValidator();
            var rename = new UpdateRuleRequest(id, "规则二", null, null, null, null);
            assertThat(validator.validate(rename)).isEmpty();
            var blank = new UpdateRuleRequest(id, " \t", null, null, null, null);
            assertThat(blank.name()).isNull();
            assertThat(validator.validate(blank)).extracting(v -> v.getMessage())
                    .containsExactly("请至少提供一个需要修改的字段");
            var blankWithOtherUpdate = new UpdateRuleRequest(id, " ", null, null, 20, null);
            assertThat(validator.validate(blankWithOtherUpdate)).isEmpty();
            var tooLong = new UpdateRuleRequest(id, "a".repeat(129), null, null, null, null);
            assertThat(validator.validate(tooLong)).extracting(v -> v.getPropertyPath().toString())
                    .containsExactly("name");
        }
    }

    @Test
    void ruleApiUsesAdvertiserOwnershipAndValidatesAdvertiserIds() throws Exception {
        var json = new ObjectMapper();
        Long advertiserId = 1L;
        var request = json.readValue("""
                {"advertiserId":"%s","name":"规则一","deviceTypes":["ANDROID"],
                 "gender":"FEMALE","ageMin":18,"ageMax":45}
                """.formatted(advertiserId), CreateRuleRequest.class);
        assertThat(request.advertiserId()).isEqualTo(advertiserId);
        try (var factory = Validation.buildDefaultValidatorFactory()) {
            assertThat(factory.getValidator().validate(request)).isEmpty();
            var invalidAdvertiser = new CreateRuleRequest(-1L, "规则一", null, null, null, null);
            assertThat(factory.getValidator().validate(invalidAdvertiser))
                    .extracting(v -> v.getPropertyPath().toString()).contains("advertiserId");
        }
        var response = new RuleResponse(1L, advertiserId, "规则一", List.of(), null, null, null, null, null);
        var fields = json.valueToTree(response);
        assertThat(fields.get("advertiserId").asText()).isEqualTo(advertiserId.toString());
        assertThat(fields.has("planId")).isFalse();
        assertThat(fields.has("regions")).isFalse();
        assertThat(fields.has("userTags")).isFalse();
    }

    @Test
    void rejectsEmptyUpdatesAndEmptyDeviceLists() {
        try (var factory = Validation.buildDefaultValidatorFactory()) {
            var validator = factory.getValidator();
            var rule = new UpdateRuleRequest(1L, null, null, null, null, null);
            var slot = new UpdateSlotRequest(1L, null, null, null, null, null);
            assertThat(validator.validate(rule)).extracting(v -> v.getMessage())
                    .contains("请至少提供一个需要修改的字段");
            assertThat(validator.validate(slot)).extracting(v -> v.getMessage())
                    .contains("请至少提供一个需要修改的字段");
            var clear = new UpdateRuleRequest(1L, null, List.of(), null, null, null);
            assertThat(validator.validate(clear)).extracting(v -> v.getPropertyPath().toString())
                    .containsExactly("deviceTypes");
        }
    }

    @Test
    void normalizesOptionalSlotStringsAndPreservesOtherUpdates() {
        var slot = new UpdateSlotRequest(1L, " ", "", 100, null, "\t");
        assertThat(slot.slotCode()).isNull();
        assertThat(slot.name()).isNull();
        assertThat(slot.scene()).isNull();
        try (var factory = Validation.buildDefaultValidatorFactory()) {
            assertThat(factory.getValidator().validate(slot)).isEmpty();
            var blank = new UpdateSlotRequest(slot.id(), " ", "", null, null, "\t");
            assertThat(factory.getValidator().validate(blank)).extracting(v -> v.getMessage())
                    .contains("请至少提供一个需要修改的字段");
        }
    }
}
