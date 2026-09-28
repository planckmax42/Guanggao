package com.example.adplatform.admin.request;

import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;

public record UpdateSlotStatusRequest(
        @NotBlank String publicId,
        @NotBlank String slotCode,
        @NotNull @Min(0) @Max(1) Integer status) {
}
