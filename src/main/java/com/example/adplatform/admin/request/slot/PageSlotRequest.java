package com.example.adplatform.admin.request.slot;

import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotNull;

public record PageSlotRequest(
        @NotNull @Min(1) Long current,
        @NotNull @Min(1) @Max(100) Long size) {
}
