package com.example.adplatform.search.event.dto;

import java.time.LocalDateTime;

public record EventSearchRequest(
        String eventId,
        String requestId,
        String eventType,
        Long planId,
        Long materialId,
        Long slotId,
        Long viewerId,
        Boolean charged,
        LocalDateTime startTime,
        LocalDateTime endTime,
        String cursor,
        Integer size) {
}
