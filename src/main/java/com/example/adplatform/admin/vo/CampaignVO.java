package com.example.adplatform.admin.vo;

import java.time.LocalDateTime;

public record CampaignVO(
        Long id,
        Long advertiserId,
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
