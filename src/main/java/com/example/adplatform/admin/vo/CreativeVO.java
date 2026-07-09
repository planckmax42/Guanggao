package com.example.adplatform.admin.vo;

import java.time.LocalDateTime;

public record CreativeVO(
        Long id,
        Long campaignId,
        Long adSlotId,
        String title,
        String description,
        String imageUrl,
        String landingPageUrl,
        String auditStatus,
        Integer status,
        LocalDateTime createdAt,
        LocalDateTime updatedAt) {
}
