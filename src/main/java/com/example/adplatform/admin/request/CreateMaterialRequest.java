package com.example.adplatform.admin.request;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;

import static com.example.adplatform.common.id.PublicIdGenerator.PLAN_PATTERN;
import static com.example.adplatform.common.id.PublicIdGenerator.SLOT_PATTERN;

public record CreateMaterialRequest(
        @NotBlank @Pattern(regexp = PLAN_PATTERN) String planPublicId,
        @NotBlank @Pattern(regexp = SLOT_PATTERN) String slotPublicId,
        @NotBlank @Size(max = 128) String title,
        @NotBlank @Size(max = 512) String description,
        @NotBlank @Size(max = 512) String imageUrl,
        @NotBlank @Size(max = 512) String landingPageUrl) {
}
