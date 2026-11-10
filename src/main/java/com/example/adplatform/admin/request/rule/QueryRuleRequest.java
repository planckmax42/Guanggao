package com.example.adplatform.admin.request.rule;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;

import static com.example.adplatform.common.id.PublicIdGenerator.PLAN_PATTERN;

public record QueryRuleRequest(
        @NotBlank @Pattern(regexp = PLAN_PATTERN) String planPublicId) {
}
