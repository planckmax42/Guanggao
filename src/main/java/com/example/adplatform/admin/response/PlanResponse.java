package com.example.adplatform.admin.response;

import java.time.LocalDateTime;

public record PlanResponse(
        Long id,
        Long userId,
        String name,
        Long budgetTotal,
        Long budgetDaily,
        Long bidPrice,
        String billingType,
        LocalDateTime startTime,
        LocalDateTime endTime,
        String status,
        LocalDateTime createdAt,
        LocalDateTime updatedAt) {
}
