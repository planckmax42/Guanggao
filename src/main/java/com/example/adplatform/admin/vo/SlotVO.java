package com.example.adplatform.admin.vo;

import java.time.LocalDateTime;

public record SlotVO(
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
