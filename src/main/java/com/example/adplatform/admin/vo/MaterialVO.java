package com.example.adplatform.admin.vo;

import java.time.LocalDateTime;

public record MaterialVO(
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
