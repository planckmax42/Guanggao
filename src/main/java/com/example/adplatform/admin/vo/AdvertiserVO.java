package com.example.adplatform.admin.vo;

import java.time.LocalDateTime;

public record AdvertiserVO(
        Long id,
        String name,
        String industry,
        String contactName,
        String contactEmail,
        Integer status,
        LocalDateTime createdAt,
        LocalDateTime updatedAt) {
}
