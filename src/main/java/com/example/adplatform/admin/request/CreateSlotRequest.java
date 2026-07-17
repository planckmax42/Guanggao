package com.example.adplatform.admin.request;

import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

public record CreateSlotRequest(
        @NotBlank @Size(max = 64) String slotCode,
        @NotBlank @Size(max = 128) String name,
        @NotNull @Min(1) @Max(10000) Integer width,
        @NotNull @Min(1) @Max(10000) Integer height,
        @NotBlank @Size(max = 64) String scene) {
}
