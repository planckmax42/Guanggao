package com.example.adplatform.admin.request;

import com.example.adplatform.admin.request.rule.CreateRuleRequest;
import com.fasterxml.jackson.databind.ObjectMapper;
import jakarta.validation.Validation;
import org.junit.jupiter.api.Test;

import java.util.Arrays;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

class CreateRuleRequestValidationTests {

    private static final String ADVERTISER_ID = "adv_" + "a".repeat(32);

    @Test
    void rejectsBlankAndTooLongNamesAndAcceptsMaximumLength() {
        try (var factory = Validation.buildDefaultValidatorFactory()) {
            var validator = factory.getValidator();
            for (String name : Arrays.asList(null, "", " ", "a".repeat(129))) {
                var request = new CreateRuleRequest(ADVERTISER_ID, name, List.of("ANDROID"), "MALE", 18, 45);
                assertThat(validator.validate(request)).extracting(v -> v.getPropertyPath().toString())
                        .containsExactly("name");
            }
            var request = new CreateRuleRequest(ADVERTISER_ID, "a".repeat(128), List.of("ANDROID"), "MALE", 18, 45);
            assertThat(validator.validate(request)).isEmpty();
        }
    }

    @Test
    void acceptsCurrentRuleFieldsWithoutRemovedRegionsAndTags() throws Exception {
        var json = new ObjectMapper();
        var request = json.readValue("""
                {"advertiserPublicId":"%s","name":"规则一","deviceTypes":["ANDROID"],
                 "gender":"MALE","ageMin":18,"ageMax":45}
                """.formatted(ADVERTISER_ID), CreateRuleRequest.class);
        try (var factory = Validation.buildDefaultValidatorFactory()) {
            assertThat(factory.getValidator().validate(request)).isEmpty();
        }
        var fields = json.valueToTree(request);
        assertThat(fields.has("regions")).isFalse();
        assertThat(fields.has("userTags")).isFalse();
    }

    @Test
    void requiresEveryFieldWithoutThrowingOnMissingAges() {
        try (var factory = Validation.buildDefaultValidatorFactory()) {
            var request = new CreateRuleRequest(null, null, null, null, null, null);
            assertThat(factory.getValidator().validate(request))
                    .extracting(v -> v.getPropertyPath().toString())
                    .containsExactlyInAnyOrder("advertiserPublicId", "name", "deviceTypes", "gender",
                            "ageMin", "ageMax");
        }
    }

    @Test
    void rejectsEmptyListsAndBlankGender() {
        try (var factory = Validation.buildDefaultValidatorFactory()) {
            var request = new CreateRuleRequest(ADVERTISER_ID, "规则一", List.of(), " ", 0, 120);
            assertThat(factory.getValidator().validate(request))
                    .extracting(v -> v.getPropertyPath().toString())
                    .containsExactlyInAnyOrder("deviceTypes", "gender");
        }
    }

    @Test
    void rejectsNullEmptyAndBlankListElements() {
        try (var factory = Validation.buildDefaultValidatorFactory()) {
            for (String value : Arrays.asList(null, "", " ")) {
                var values = Arrays.asList(value);
                var request = new CreateRuleRequest(ADVERTISER_ID, "规则一", values, "FEMALE", 18, 45);
                assertThat(factory.getValidator().validate(request))
                        .extracting(v -> v.getPropertyPath().toString())
                        .containsExactlyInAnyOrder("deviceTypes[0].<list element>");
            }
        }
    }

    @Test
    void acceptsCompleteRequestAndStillValidatesAgeRange() {
        try (var factory = Validation.buildDefaultValidatorFactory()) {
            var valid = new CreateRuleRequest(ADVERTISER_ID, "规则一", List.of("ANDROID"),
                    "FEMALE", 0, 120);
            assertThat(factory.getValidator().validate(valid)).isEmpty();
            var invalid = new CreateRuleRequest(ADVERTISER_ID, "规则一", valid.deviceTypes(),
                    valid.gender(), 45, 18);
            assertThat(factory.getValidator().validate(invalid))
                    .extracting(v -> v.getMessage()).containsExactly("最大年龄必须大于等于最小年龄");
        }
    }
}
