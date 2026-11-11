package com.example.adplatform.admin.request.plan;

import jakarta.validation.constraints.AssertTrue;
import jakarta.validation.constraints.Future;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;

import java.time.LocalDateTime;

import static com.example.adplatform.common.id.PublicIdGenerator.PLAN_PATTERN;

public record UpdatePlanRequest(
        @NotBlank @Pattern(regexp = PLAN_PATTERN) String publicId,
        @Size(max = 128) String name,
        @Min(1) Long budgetTotal,
        @Min(1) Long budgetDaily,
        @Min(1) Long bidPrice,
        @Size(max = 16) String billingType,
        LocalDateTime startTime,
        @Future LocalDateTime endTime) {

    @AssertTrue(message = "结束时间必须晚于开始时间")
    public boolean isValidTimeRange() {
        return startTime == null || endTime == null || endTime.isAfter(startTime);
    }

    @AssertTrue(message = "日预算必须小于等于总预算")
    public boolean isValidBudget() {
        return budgetDaily == null || budgetTotal == null || budgetDaily <= budgetTotal;
    }
}
