package com.example.adplatform.search.outbox.message;

public record ConfigChangeMessage(
        String eventId,
        ConfigAggregateType aggregateType,
        Long aggregateId) {
}
