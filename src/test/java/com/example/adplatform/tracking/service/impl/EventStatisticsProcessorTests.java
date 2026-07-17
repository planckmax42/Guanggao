package com.example.adplatform.tracking.service.impl;

import com.example.adplatform.admin.entity.MaterialEntity;
import com.example.adplatform.admin.entity.PlanEntity;
import com.example.adplatform.tracking.entity.EventType;
import com.example.adplatform.tracking.message.EventMessage;
import com.example.adplatform.tracking.service.EventContextResolver;
import com.example.adplatform.tracking.service.EventProcessingContext;
import com.example.adplatform.tracking.service.EventStatisticsStore;
import org.junit.jupiter.api.Test;

import java.time.LocalDateTime;

import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class EventStatisticsProcessorTests {

    @Test
    void shouldDelegateToIdempotentStatisticsStore() {
        EventContextResolver resolver = mock(EventContextResolver.class);
        EventStatisticsStore store = mock(EventStatisticsStore.class);
        EventMessage message = new EventMessage(
                "event-1", "request-1", "IMPRESSION", 10L, 20L, LocalDateTime.now());
        MaterialEntity material = new MaterialEntity();
        material.setId(10L);
        material.setPlanId(30L);
        material.setSlotId(40L);
        PlanEntity plan = new PlanEntity();
        plan.setId(30L);
        EventProcessingContext context = new EventProcessingContext(
                EventType.IMPRESSION,
                material,
                plan,
                "CPM",
                message.eventTime(),
                message.eventTime().toLocalDate());
        when(resolver.resolve(message)).thenReturn(context);

        new EventStatisticsProcessorImpl(resolver, store).record(message);

        verify(store).recordEventOnce("event-1", 20L, context);
    }
}
