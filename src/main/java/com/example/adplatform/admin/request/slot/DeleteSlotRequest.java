package com.example.adplatform.admin.request.slot;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;

import static com.example.adplatform.common.util.PublicIdUtils.SLOT_PATTERN;

public record DeleteSlotRequest(
        @NotBlank @Pattern(regexp = SLOT_PATTERN) String publicId) {
}
