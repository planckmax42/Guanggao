package com.example.adplatform.admin.response.material;

import java.time.LocalDateTime;

public record MaterialQueryResponse(
        String publicId,
        String advertiserPublicId,
        String slotPublicId,
        String name,
        String description,
        String imageUrl,
        String landingPageUrl,
        String auditStatus,
        Integer status) {
}
