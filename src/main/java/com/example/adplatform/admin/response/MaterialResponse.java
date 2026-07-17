package com.example.adplatform.admin.response;

import java.time.LocalDateTime;

public record MaterialResponse(
        Long id,
        Long planId,
        Long slotId,
        String title,
        String description,
        String imageUrl,
        String landingPageUrl,
        String auditStatus,
        Integer status,
        LocalDateTime createdAt,
        LocalDateTime updatedAt) {
}
