package com.example.adplatform.admin.request.slot;

import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;

import static com.example.adplatform.common.id.PublicIdGenerator.SLOT_PATTERN;

public record UpdateSlotStatusRequest(
        @NotBlank @Pattern(regexp = SLOT_PATTERN) String publicId,
        @NotBlank String slotCode,
        @NotNull @Min(0) @Max(1) Integer status) {
}
