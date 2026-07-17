package com.example.adplatform.tracking.service.impl;

import com.example.adplatform.admin.entity.MaterialEntity;
import com.example.adplatform.admin.entity.PlanEntity;
import com.example.adplatform.infra.redis.budget.BudgetRedisService;
import com.example.adplatform.report.mapper.DailyReportMapper;
import com.example.adplatform.tracking.entity.ChargeRecordEntity;
import com.example.adplatform.tracking.entity.ChargeStatus;
import com.example.adplatform.tracking.entity.EventType;
import com.example.adplatform.tracking.mapper.ChargeRecordMapper;
import com.example.adplatform.tracking.message.EventMessage;
import com.example.adplatform.tracking.service.EventContextResolver;
import com.example.adplatform.tracking.service.EventProcessingContext;
import com.example.adplatform.tracking.service.EventStatisticsStore;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.time.LocalDate;
import java.time.LocalDateTime;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class EventBillingProcessorTests {

    private EventContextResolver contextResolver;
    private ChargeRecordMapper chargeRecordMapper;
    private BudgetRedisService budgetRedisService;
    private EventStatisticsStore statisticsStore;
    private EventBillingProcessorImpl processor;
    private EventMessage message;
    private EventProcessingContext context;

    @BeforeEach
    void setUp() {
        contextResolver = mock(EventContextResolver.class);
        chargeRecordMapper = mock(ChargeRecordMapper.class);
        budgetRedisService = mock(BudgetRedisService.class);
        statisticsStore = mock(EventStatisticsStore.class);
        processor = new EventBillingProcessorImpl(
                contextResolver,
                chargeRecordMapper,
                mock(DailyReportMapper.class),
                budgetRedisService,
                statisticsStore);

        LocalDateTime eventTime = LocalDateTime.of(2026, 7, 17, 12, 0);
        message = new EventMessage("event-1", "request-1", EventType.CLICK, 10L, 20L, eventTime);
        MaterialEntity material = new MaterialEntity();
        material.setId(10L);
        material.setPlanId(30L);
        material.setSlotId(40L);
        PlanEntity plan = new PlanEntity();
        plan.setId(30L);
        plan.setBillingType("CPC");
        plan.setBidPrice(25L);
        plan.setBudgetDaily(10_000L);
        plan.setBudgetTotal(100_000L);
        context = new EventProcessingContext(
                material,
                plan,
                "CPC",
                eventTime,
                LocalDate.of(2026, 7, 17));
        when(contextResolver.resolve(message)).thenReturn(context);
    }

    @Test
    void shouldCreateChargeAndRecordCostStatistics() {
        when(chargeRecordMapper.selectByEventId("event-1")).thenReturn(null);
        when(budgetRedisService.tryChargeOnce("event-1", context.plan(), context.statDate(), 25L))
                .thenReturn(true);

        processor.bill(message);

        verify(chargeRecordMapper).insert(any(ChargeRecordEntity.class));
        verify(statisticsStore).recordCostOnce("event-1", context, 25L);
    }

    @Test
    void shouldReuseExistingChargeWithoutChargingBudgetAgain() {
        ChargeRecordEntity existing = new ChargeRecordEntity();
        existing.setEventId("event-1");
        existing.setAmount(25L);
        existing.setChargeStatus(ChargeStatus.SUCCESS.name());
        when(chargeRecordMapper.selectByEventId("event-1")).thenReturn(existing);

        processor.bill(message);

        verify(budgetRedisService, never()).tryChargeOnce(any(), any(), any(), eq(25L));
        verify(chargeRecordMapper, never()).insert(any(ChargeRecordEntity.class));
        verify(statisticsStore).recordCostOnce("event-1", context, 25L);
    }
}
