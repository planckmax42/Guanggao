package com.example.adplatform.admin.response;

import java.time.LocalDateTime;

public record AdvertiserResponse(
        String publicId,
        String name,
        String industry,
        String contactName,
        String contactEmail,
        Integer status,
        LocalDateTime createdAt,
        LocalDateTime updatedAt) {
}
