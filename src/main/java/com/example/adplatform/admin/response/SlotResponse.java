package com.example.adplatform.admin.response;

import java.time.LocalDateTime;

public record SlotResponse(
        Long id,
        String slotCode,
        String name,
        Integer width,
        Integer height,
        String scene,
        Integer status,
        LocalDateTime createdAt,
        LocalDateTime updatedAt) {
}
