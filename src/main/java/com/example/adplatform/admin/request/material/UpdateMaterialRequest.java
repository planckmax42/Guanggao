package com.example.adplatform.admin.request.material;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.AssertTrue;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;

import static com.example.adplatform.common.util.StringUtils.blankToNull;

import static com.example.adplatform.common.util.PublicIdUtils.SLOT_PATTERN;
import static com.example.adplatform.common.util.PublicIdUtils.MATERIAL_PATTERN;

public record UpdateMaterialRequest(
        @NotBlank @Pattern(regexp = MATERIAL_PATTERN) String publicId,
        @Pattern(regexp = SLOT_PATTERN) String slotPublicId,
        @Size(max = 128) String name,
        @Size(max = 512) String description,
        @Size(max = 512) String imageUrl,
        @Size(max = 512) String landingPageUrl) {

    public UpdateMaterialRequest {
        slotPublicId = blankToNull(slotPublicId);
        name = blankToNull(name);
        description = blankToNull(description);
        imageUrl = blankToNull(imageUrl);
        landingPageUrl = blankToNull(landingPageUrl);
    }

    @AssertTrue(message = "请至少提供一个需要修改的字段")
    public boolean isUpdateProvided() {
        return slotPublicId != null || name != null || description != null
                || imageUrl != null || landingPageUrl != null;
    }
}
