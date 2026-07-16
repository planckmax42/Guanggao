package com.example.adplatform.search.candidate.event;

import com.example.adplatform.search.outbox.message.ConfigAggregateType;

public record ConfigStopGuardEvent(
        ConfigAggregateType aggregateType,
        Long aggregateId,
        boolean stopped) {
}
