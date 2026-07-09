package com.example.adplatform.tracking.vo;

public record AdEventResponse(
        String eventId,
        String eventType,
        Boolean duplicate,
        Boolean charged,
        Long costAmount,
        String billingType) {
}
