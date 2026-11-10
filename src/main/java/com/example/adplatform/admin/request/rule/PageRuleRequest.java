package com.example.adplatform.admin.request.rule;

import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;

public record PageRuleRequest(
        @Min(1) Long current,
        @Min(1) @Max(100) Long size) {

    public PageRuleRequest {
        current = current == null ? 1L : current;
        size = size == null ? 10L : size;
    }
}
