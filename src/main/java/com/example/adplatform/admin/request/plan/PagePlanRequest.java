package com.example.adplatform.admin.request.plan;

import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;

public record PagePlanRequest(
        @Min(1) Long current,
        @Min(1) @Max(100) Long size) {

    public PagePlanRequest {
        current = current == null ? 1L : current;
        size = size == null ? 10L : size;
    }
}
