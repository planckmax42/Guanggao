package com.example.adplatform.tracking.response;

public record EventResponse(
        String eventId,
        String eventType,
        Boolean duplicate,
        String billingType) {
}
