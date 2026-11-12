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
        String publicId = "rule_" + "a".repeat(32);
        try (var factory = Validation.buildDefaultValidatorFactory()) {
            var validator = factory.getValidator();
            var rename = new UpdateRuleRequest(publicId, "规则二", null, null, null, null);
            assertThat(validator.validate(rename)).isEmpty();
            var blank = new UpdateRuleRequest(publicId, " \t", null, null, null, null);
            assertThat(blank.name()).isNull();
            assertThat(validator.validate(blank)).extracting(v -> v.getMessage())
                    .containsExactly("请至少提供一个需要修改的字段");
            var blankWithOtherUpdate = new UpdateRuleRequest(publicId, " ", null, null, 20, null);
            assertThat(validator.validate(blankWithOtherUpdate)).isEmpty();
            var tooLong = new UpdateRuleRequest(publicId, "a".repeat(129), null, null, null, null);
            assertThat(validator.validate(tooLong)).extracting(v -> v.getPropertyPath().toString())
                    .containsExactly("name");
        }
    }

    @Test
    void ruleApiUsesAdvertiserOwnershipAndValidatesAdvertiserIds() throws Exception {
        var json = new ObjectMapper();
        String advertiserId = "adv_" + "a".repeat(32);
        var request = json.readValue("""
                {"advertiserPublicId":"%s","name":"规则一","deviceTypes":["ANDROID"],
                 "gender":"FEMALE","ageMin":18,"ageMax":45}
                """.formatted(advertiserId), CreateRuleRequest.class);
        assertThat(request.advertiserPublicId()).isEqualTo(advertiserId);
        try (var factory = Validation.buildDefaultValidatorFactory()) {
            assertThat(factory.getValidator().validate(request)).isEmpty();
            var oldPlanId = new CreateRuleRequest("plan_" + "a".repeat(32), "规则一", null, null, null, null);
            assertThat(factory.getValidator().validate(oldPlanId))
                    .extracting(v -> v.getPropertyPath().toString()).contains("advertiserPublicId");
        }
        var response = new RuleResponse("rule_1", advertiserId, "规则一", List.of(), null, null, null, null, null);
        var fields = json.valueToTree(response);
        assertThat(fields.get("advertiserPublicId").asText()).isEqualTo(advertiserId);
        assertThat(fields.has("planPublicId")).isFalse();
        assertThat(fields.has("regions")).isFalse();
        assertThat(fields.has("userTags")).isFalse();
    }

    @Test
    void rejectsEmptyUpdatesButAllowsExplicitListClearing() {
        try (var factory = Validation.buildDefaultValidatorFactory()) {
            var validator = factory.getValidator();
            var rule = new UpdateRuleRequest("rule_" + "a".repeat(32), null, null, null, null, null);
            var slot = new UpdateSlotRequest("slot_" + "a".repeat(32), null, null, null, null, null);
            assertThat(validator.validate(rule)).extracting(v -> v.getMessage())
                    .contains("请至少提供一个需要修改的字段");
            assertThat(validator.validate(slot)).extracting(v -> v.getMessage())
                    .contains("请至少提供一个需要修改的字段");
            var clear = new UpdateRuleRequest("rule_" + "a".repeat(32), null, List.of(), null, null, null);
            assertThat(validator.validate(clear)).isEmpty();
        }
    }

    @Test
    void normalizesOptionalSlotStringsAndPreservesOtherUpdates() {
        var slot = new UpdateSlotRequest("slot_" + "a".repeat(32), " ", "", 100, null, "\t");
        assertThat(slot.slotCode()).isNull();
        assertThat(slot.name()).isNull();
        assertThat(slot.scene()).isNull();
        try (var factory = Validation.buildDefaultValidatorFactory()) {
            assertThat(factory.getValidator().validate(slot)).isEmpty();
            var blank = new UpdateSlotRequest(slot.publicId(), " ", "", null, null, "\t");
            assertThat(factory.getValidator().validate(blank)).extracting(v -> v.getMessage())
                    .contains("请至少提供一个需要修改的字段");
        }
    }
}
