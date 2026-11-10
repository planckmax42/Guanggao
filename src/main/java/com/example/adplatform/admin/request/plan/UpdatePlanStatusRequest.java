package com.example.adplatform.admin.request.plan;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;

import static com.example.adplatform.common.id.PublicIdGenerator.PLAN_PATTERN;

public record UpdatePlanStatusRequest(
        @NotBlank @Pattern(regexp = PLAN_PATTERN) String publicId) {
}
