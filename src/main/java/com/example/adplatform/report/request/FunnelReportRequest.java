package com.example.adplatform.report.request;

import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;

import java.time.LocalDate;

import static com.example.adplatform.common.id.PublicIdGenerator.PLAN_PATTERN;

public record FunnelReportRequest(
        @NotNull LocalDate startDate,
        @NotNull LocalDate endDate,
        @Pattern(regexp = PLAN_PATTERN) String planPublicId) {
}
