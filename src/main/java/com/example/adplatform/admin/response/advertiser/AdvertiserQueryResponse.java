package com.example.adplatform.admin.response.advertiser;

import java.time.LocalDateTime;

public record AdvertiserQueryResponse(
        String publicId,
        String name,
        String industry,
        String contactName,
        String contactEmail,
        Integer status,
        LocalDateTime createdAt,
        LocalDateTime updatedAt) {
}
