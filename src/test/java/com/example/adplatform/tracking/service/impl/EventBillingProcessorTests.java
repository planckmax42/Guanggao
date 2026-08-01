package com.example.adplatform.tracking.service.impl;

import com.example.adplatform.tracking.port.BudgetChargePort;
import com.example.adplatform.tracking.port.EventMetadataReaderPort;
import com.example.adplatform.report.mapper.DailyReportMapper;
import com.example.adplatform.tracking.entity.ChargeRecordEntity;
import com.example.adplatform.tracking.entity.ChargeStatus;
import com.example.adplatform.tracking.entity.EventType;
import com.example.adplatform.tracking.mapper.ChargeRecordMapper;
import com.example.adplatform.tracking.message.EventMessage;
import com.example.adplatform.tracking.service.EventMaterialMetadata;
import com.example.adplatform.tracking.port.EventStatisticsStore;
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

    private EventMetadataReaderPort metadataCacheService;
    private ChargeRecordMapper chargeRecordMapper;
    private BudgetChargePort budgetRedisService;
    private EventStatisticsStore statisticsStore;
    private EventBillingProcessorImpl processor;
    private EventMessage message;
    private EventMaterialMetadata metadata;

    @BeforeEach
    void setUp() {
        metadataCacheService = mock(EventMetadataReaderPort.class);
        chargeRecordMapper = mock(ChargeRecordMapper.class);
        budgetRedisService = mock(BudgetChargePort.class);
        statisticsStore = mock(EventStatisticsStore.class);
        processor = new EventBillingProcessorImpl(
                metadataCacheService,
                chargeRecordMapper,
                mock(DailyReportMapper.class),
                budgetRedisService,
                statisticsStore);

        LocalDateTime eventTime = LocalDateTime.of(2026, 7, 17, 12, 0);
        message = new EventMessage("event-1", "request-1", EventType.CLICK, 10L, 20L, eventTime);
        metadata = new EventMaterialMetadata(
                30L, 40L, 100_000L, 10_000L, 25L, "CPC");
        when(metadataCacheService.get(message.materialId())).thenReturn(metadata);
    }

    @Test
    void shouldCreateChargeAndRecordCostStatistics() {
        when(chargeRecordMapper.selectByEventId("event-1")).thenReturn(null);
        when(budgetRedisService.tryChargeOnce(
                "event-1", 30L, 10_000L, 100_000L, LocalDate.of(2026, 7, 17), 25L))
                .thenReturn(true);

        processor.bill(message);

        verify(chargeRecordMapper).insert(any(ChargeRecordEntity.class));
        verify(statisticsStore).recordCostOnce(message, metadata, 25L);
    }

    @Test
    void shouldReuseExistingChargeWithoutChargingBudgetAgain() {
        ChargeRecordEntity existing = new ChargeRecordEntity();
        existing.setEventId("event-1");
        existing.setAmount(25L);
        existing.setChargeStatus(ChargeStatus.SUCCESS.name());
        when(chargeRecordMapper.selectByEventId("event-1")).thenReturn(existing);

        processor.bill(message);

        verify(budgetRedisService, never()).tryChargeOnce(
                any(), any(), any(), any(), any(), eq(25L));
        verify(chargeRecordMapper, never()).insert(any(ChargeRecordEntity.class));
        verify(statisticsStore).recordCostOnce(message, metadata, 25L);
    }
}
