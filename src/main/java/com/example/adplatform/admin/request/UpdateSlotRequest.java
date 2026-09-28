package com.example.adplatform.admin.request;

import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

public record UpdateSlotRequest(
        @NotBlank String publicId,
         @Size(max = 64) String slotCode,
         @Size(max = 128) String name,
         @Min(1) @Max(10000) Integer width,
         @Min(1) @Max(10000) Integer height,
         @Size(max = 64) String scene) {
}
