package com.example.adplatform.admin.request.plan;

import jakarta.validation.constraints.AssertTrue;
import jakarta.validation.constraints.Future;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;

import java.time.LocalDateTime;

import static com.example.adplatform.common.id.PublicIdGenerator.ADVERTISER_PATTERN;

public record CreatePlanRequest(
        @NotBlank @Pattern(regexp = ADVERTISER_PATTERN) String advertiserPublicId,
        @NotBlank @Size(max = 128) String name,
        @NotNull @Min(1) Long budgetTotal,
        @NotNull @Min(1) Long budgetDaily,
        @NotNull @Min(1) Long bidPrice,
        @NotBlank
        @Pattern(regexp = "CPC|CPM|CPA", message = "计费方式只能是 CPC、CPM 或 CPA")
        String billingType,
        @NotNull LocalDateTime startTime,
        @NotNull @Future LocalDateTime endTime) {

    @AssertTrue(message = "结束时间必须晚于开始时间")
    public boolean isValidTimeRange() {
        return startTime == null || endTime == null || endTime.isAfter(startTime);
    }

    @AssertTrue(message = "日预算必须小于等于总预算")
    public boolean isValidBudget() {
        return budgetDaily == null || budgetTotal == null || budgetDaily <= budgetTotal;
    }
}
