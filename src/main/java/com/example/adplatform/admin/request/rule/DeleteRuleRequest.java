package com.example.adplatform.admin.request.rule;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;

import static com.example.adplatform.common.util.PublicIdUtils.RULE_PATTERN;

public record DeleteRuleRequest(
        @NotBlank @Pattern(regexp = RULE_PATTERN) String publicId) {
}
