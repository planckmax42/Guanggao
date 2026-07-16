package com.example.adplatform.search.event.vo;

import java.time.LocalDateTime;

public record EventSearchItemVO(
        String eventId,
        String requestId,
        String eventType,
        Long planId,
        Long materialId,
        Long slotId,
        Long viewerId,
        String billingType,
        Boolean charged,
        Long costAmount,
        LocalDateTime eventTime,
        LocalDateTime createdAt) {
}
