package com.example.adplatform.admin.response.plan;

import com.fasterxml.jackson.databind.annotation.JsonSerialize;
import com.fasterxml.jackson.databind.ser.std.ToStringSerializer;

import java.time.LocalDateTime;

public record PlanResponse(
        @JsonSerialize(using = ToStringSerializer.class) Long id,
        @JsonSerialize(using = ToStringSerializer.class) Long advertiserId,
        String name,
        Long budgetTotal,
        Long budgetDaily,
        Long bidPrice,
        String billingType,
        LocalDateTime startTime,
        LocalDateTime endTime,
        String status) {
}
