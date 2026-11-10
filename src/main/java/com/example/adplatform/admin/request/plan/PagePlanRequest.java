package com.example.adplatform.admin.request.plan;

import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.Pattern;

import static com.example.adplatform.common.id.PublicIdGenerator.ADVERTISER_PATTERN;

public record PagePlanRequest(
        @Min(1) Long current,
        @Min(1) @Max(100) Long size,
        @Pattern(regexp = ADVERTISER_PATTERN) String advertiserPublicId,
        String status) {

    public PagePlanRequest {
        current = current == null ? 1L : current;
        size = size == null ? 10L : size;
    }
}
