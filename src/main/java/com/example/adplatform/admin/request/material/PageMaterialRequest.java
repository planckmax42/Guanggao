package com.example.adplatform.admin.request.material;

import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.Pattern;

import static com.example.adplatform.common.id.PublicIdGenerator.PLAN_PATTERN;

public record PageMaterialRequest(
        @Min(1) Long current,
        @Min(1) @Max(100) Long size,
        @Pattern(regexp = PLAN_PATTERN) String planPublicId,
        String auditStatus) {

    public PageMaterialRequest {
        current = current == null ? 1L : current;
        size = size == null ? 10L : size;
    }
}
