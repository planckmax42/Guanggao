package com.example.adplatform.tracking.service.impl;

import com.example.adplatform.admin.entity.MaterialEntity;
import com.example.adplatform.admin.entity.PlanEntity;
import com.example.adplatform.tracking.converter.EventConverter;
import com.example.adplatform.tracking.entity.ChargeRecordEntity;
import com.example.adplatform.tracking.entity.ChargeStatus;
import com.example.adplatform.tracking.entity.EventEntity;
import com.example.adplatform.tracking.entity.EventType;
import com.example.adplatform.tracking.mapper.ChargeRecordMapper;
import com.example.adplatform.tracking.mapper.EventMapper;
import com.example.adplatform.tracking.message.EventMessage;
import com.example.adplatform.tracking.service.EventContextResolver;
import com.example.adplatform.tracking.service.EventProcessingContext;
import org.junit.jupiter.api.Test;

import java.time.LocalDate;
import java.time.LocalDateTime;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class EventArchiveProcessorTests {

    @Test
    void shouldApplyChargeThatArrivedBeforeArchive() {
        EventContextResolver resolver = mock(EventContextResolver.class);
        EventConverter converter = mock(EventConverter.class);
        EventMapper eventMapper = mock(EventMapper.class);
        ChargeRecordMapper chargeRecordMapper = mock(ChargeRecordMapper.class);
        EventMessage message = new EventMessage(
                "event-1", "request-1", "CLICK", 10L, 20L, LocalDateTime.now());
        MaterialEntity material = new MaterialEntity();
        material.setId(10L);
        material.setPlanId(30L);
        material.setSlotId(40L);
        PlanEntity plan = new PlanEntity();
        plan.setId(30L);
        EventProcessingContext context = new EventProcessingContext(
                EventType.CLICK, material, plan, "CPC", message.eventTime(), LocalDate.now());
        ChargeRecordEntity charge = new ChargeRecordEntity();
        charge.setEventId("event-1");
        charge.setAmount(25L);
        charge.setChargeStatus(ChargeStatus.SUCCESS.name());
        when(resolver.resolve(message)).thenReturn(context);
        when(converter.toEntity(any(), any(), any(), any(), any(Boolean.class), any(Long.class), any()))
                .thenReturn(new EventEntity());
        when(chargeRecordMapper.selectByEventId("event-1")).thenReturn(charge);

        new EventArchiveProcessorImpl(resolver, converter, eventMapper, chargeRecordMapper).archive(message);

        verify(eventMapper).insert(any(EventEntity.class));
        verify(eventMapper).updateChargeResult("event-1", 1, 25L);
    }
}
