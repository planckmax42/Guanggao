package com.example.adplatform.admin.request.plan;

import jakarta.validation.constraints.Positive;
import com.fasterxml.jackson.databind.annotation.JsonDeserialize;
import com.example.adplatform.admin.json.AdminIdDeserializer;
import com.example.adplatform.admin.enums.BillingType;
import jakarta.validation.constraints.AssertTrue;
import jakarta.validation.constraints.Future;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;

import java.time.LocalDateTime;


public record CreatePlanRequest(
        @NotNull @Positive @JsonDeserialize(using = AdminIdDeserializer.class) Long advertiserId,
        @NotBlank @Size(max = 128) String name,
        @NotNull @Min(1) Long budgetTotal,
        @NotNull @Min(1) Long budgetDaily,
        @NotNull @Min(1) Long bidPrice,
        @NotBlank
        @Pattern(regexp = BillingType.PATTERN, message = "计费方式只能是 CPC、CPM 或 CPA")
        String billingType,
        @NotNull LocalDateTime startTime,
        @NotNull @Future LocalDateTime endTime) {

    public CreatePlanRequest {
        billingType = BillingType.normalizeInput(billingType);
    }

    @AssertTrue(message = "结束时间必须晚于开始时间")
    public boolean isValidTimeRange() {
        return startTime == null || endTime == null || endTime.isAfter(startTime);
    }

    @AssertTrue(message = "日预算必须小于等于总预算")
    public boolean isValidBudget() {
        return budgetDaily == null || budgetTotal == null || budgetDaily <= budgetTotal;
    }
}
