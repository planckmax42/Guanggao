package com.example.adplatform.admin.request.slot;

import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;

public record PageSlotRequest(
        @Min(1) Long current,
        @Min(1) @Max(100) Long size,
        String slotCode,
        Integer status) {

    public PageSlotRequest {
        current = current == null ? 1L : current;
        size = size == null ? 10L : size;
    }
}
