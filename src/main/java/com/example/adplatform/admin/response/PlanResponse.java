package com.example.adplatform.admin.response;

import java.time.LocalDateTime;

public record PlanResponse(
        String publicId,
        String advertiserPublicId,
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
