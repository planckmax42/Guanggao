package com.example.adplatform.admin.request.plan;

import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Positive;
import com.fasterxml.jackson.databind.annotation.JsonDeserialize;
import com.example.adplatform.admin.json.AdminIdDeserializer;
import com.example.adplatform.admin.enums.BillingType;
import jakarta.validation.constraints.AssertTrue;
import jakarta.validation.constraints.Future;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;

import java.time.LocalDateTime;

import static com.example.adplatform.common.util.StringUtils.blankToNull;


public record UpdatePlanRequest(
        @NotNull @Positive @JsonDeserialize(using = AdminIdDeserializer.class) Long id,
        @Size(max = 128) String name,
        @Min(1) Long budgetTotal,
        @Min(1) Long budgetDaily,
        @Min(1) Long bidPrice,
        @Pattern(regexp = BillingType.PATTERN, message = "计费方式只能是 CPC、CPM 或 CPA")
        String billingType,
        LocalDateTime startTime,
        @Future LocalDateTime endTime) {

    public UpdatePlanRequest {
        name = blankToNull(name);
        billingType = blankToNull(BillingType.normalizeInput(billingType));
    }

    @AssertTrue(message = "请至少提供一个需要修改的字段")
    public boolean isUpdateProvided() {
        return name != null || budgetTotal != null || budgetDaily != null || bidPrice != null
                || billingType != null || startTime != null || endTime != null;
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
