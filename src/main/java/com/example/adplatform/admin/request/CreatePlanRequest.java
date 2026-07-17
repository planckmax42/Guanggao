package com.example.adplatform.admin.request;

import jakarta.validation.constraints.AssertTrue;
import jakarta.validation.constraints.Future;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

import java.time.LocalDateTime;

public record CreatePlanRequest(
        @NotNull Long userId,
        @NotBlank @Size(max = 128) String name,
        @NotNull @Min(1) Long budgetTotal,
        @NotNull @Min(1) Long budgetDaily,
        @NotNull @Min(1) Long bidPrice,
        @Size(max = 16) String billingType,
        @NotNull LocalDateTime startTime,
        @NotNull @Future LocalDateTime endTime) {

    @AssertTrue(message = "结束时间必须晚于开始时间")
    public boolean isValidTimeRange() {
        return startTime == null || endTime == null || endTime.isAfter(startTime);
    }

    @AssertTrue(message = "日预算必须小于等于总预算")
    public boolean isValidBudget() {
        return budgetTotal == null || budgetDaily == null || budgetDaily <= budgetTotal;
    }
}
