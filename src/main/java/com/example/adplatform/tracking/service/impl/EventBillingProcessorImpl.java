package com.example.adplatform.tracking.service.impl;

import com.example.adplatform.admin.entity.BillingType;
import com.example.adplatform.infra.redis.budget.BudgetRedisService;
import com.example.adplatform.infra.redis.event.EventMetadataCacheService;
import com.example.adplatform.report.mapper.DailyReportMapper;
import com.example.adplatform.tracking.entity.ChargeRecordEntity;
import com.example.adplatform.tracking.entity.ChargeStatus;
import com.example.adplatform.tracking.entity.EventType;
import com.example.adplatform.tracking.mapper.ChargeRecordMapper;
import com.example.adplatform.tracking.message.EventMessage;
import com.example.adplatform.tracking.service.EventBillingProcessor;
import com.example.adplatform.tracking.service.EventMaterialMetadata;
import com.example.adplatform.tracking.service.EventStatisticsStore;
import lombok.RequiredArgsConstructor;
import org.springframework.dao.DuplicateKeyException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/** 计费链路：独立判断预算并幂等写入 charge_record。 */
@RequiredArgsConstructor
@Service
public class EventBillingProcessorImpl implements EventBillingProcessor {

    private final EventMetadataCacheService metadataCacheService;
    private final ChargeRecordMapper chargeRecordMapper;
    private final DailyReportMapper dailyReportMapper;
    private final BudgetRedisService budgetRedisService;
    private final EventStatisticsStore eventStatisticsStore;

    @Override
    @Transactional(rollbackFor = Exception.class)
    public void bill(EventMessage message) {
        message = message.withDefaultEventTime();
        EventMaterialMetadata metadata = metadataCacheService.get(message.materialId());
        long costAmount = calculateCostAmount(message, metadata);
        if (costAmount <= 0) {
            return;
        }

        ChargeRecordEntity charge = chargeRecordMapper.selectByEventId(message.eventId());
        if (charge == null) {
            boolean charged = budgetRedisService.tryChargeOnce(
                    message.eventId(),
                    metadata.planId(),
                    metadata.budgetDaily(),
                    metadata.budgetTotal(),
                    message.eventTime().toLocalDate(),
                    costAmount);
            charge = toChargeRecord(
                    message,
                    metadata,
                    charged ? costAmount : 0L,
                    charged ? ChargeStatus.SUCCESS : ChargeStatus.BUDGET_EXHAUSTED);
            try {
                chargeRecordMapper.insert(charge);
            } catch (DuplicateKeyException ignored) {
                charge = chargeRecordMapper.selectByEventId(message.eventId());
            }
        }

        if (charge == null) {
            throw new IllegalStateException("计费流水写入后无法读取，eventId=" + message.eventId());
        }
        recordCostStatistics(message, metadata, charge);
    }

    private long calculateCostAmount(EventMessage message, EventMaterialMetadata metadata) {
        String billingType = BillingType.normalizeOrDefault(metadata.billingType());
        if (BillingType.CPC.name().equals(billingType)
                && message.eventType() == EventType.CLICK) {
            return metadata.bidPrice();
        }
        if (BillingType.CPA.name().equals(billingType)
                && message.eventType() == EventType.CONVERSION) {
            return metadata.bidPrice();
        }
        if (BillingType.CPM.name().equals(billingType)
                && message.eventType() == EventType.IMPRESSION) {
            long currentImpressions = dailyReportMapper.sumImpressionsByPlan(
                    message.eventTime().toLocalDate(), metadata.planId());
            return (currentImpressions + 1) % 1000 == 0 ? metadata.bidPrice() : 0L;
        }
        return 0L;
    }

    private void recordCostStatistics(
            EventMessage message,
            EventMaterialMetadata metadata,
            ChargeRecordEntity charge) {
        boolean charged = ChargeStatus.SUCCESS.name().equals(charge.getChargeStatus());
        long finalCost = charged ? charge.getAmount() : 0L;
        eventStatisticsStore.recordCostOnce(message, metadata, finalCost);
    }

    private ChargeRecordEntity toChargeRecord(
            EventMessage message,
            EventMaterialMetadata metadata,
            long amount,
            ChargeStatus status) {
        ChargeRecordEntity record = new ChargeRecordEntity();
        record.setEventId(message.eventId());
        record.setPlanId(metadata.planId());
        record.setMaterialId(message.materialId());
        record.setSlotId(metadata.slotId());
        record.setBillingType(BillingType.normalizeOrDefault(metadata.billingType()));
        record.setAmount(amount);
        record.setChargeStatus(status.name());
        record.setChargeTime(message.eventTime());
        return record;
    }
}
