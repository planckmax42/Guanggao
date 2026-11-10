package com.example.adplatform.admin.request.material;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;

import static com.example.adplatform.common.id.PublicIdGenerator.ADVERTISER_PATTERN;
import static com.example.adplatform.common.id.PublicIdGenerator.SLOT_PATTERN;
import static com.example.adplatform.common.id.PublicIdGenerator.MATERIAL_PATTERN;

public record UpdateMaterialRequest(
        @NotBlank @Pattern(regexp = MATERIAL_PATTERN) String publicId,
        @NotBlank @Pattern(regexp = ADVERTISER_PATTERN) String advertiserPublicId,
        @NotBlank @Pattern(regexp = SLOT_PATTERN) String slotPublicId,
        @NotBlank @Size(max = 128) String name,
        @NotBlank @Size(max = 512) String description,
        @NotBlank @Size(max = 512) String imageUrl,
        @NotBlank @Size(max = 512) String landingPageUrl) {
}
