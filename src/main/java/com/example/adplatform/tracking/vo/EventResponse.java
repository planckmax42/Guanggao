package com.example.adplatform.tracking.vo;

public record EventResponse(
        String eventId,
        String eventType,
        Boolean duplicate,
        Boolean charged,
        Long costAmount,
        String billingType) {
}
