package com.example.adplatform.admin.request.material;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;

import static com.example.adplatform.common.util.PublicIdUtils.MATERIAL_PATTERN;

public record DeleteMaterialRequest(
        @NotBlank @Pattern(regexp = MATERIAL_PATTERN) String publicId) {
}
