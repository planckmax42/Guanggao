package com.example.adplatform.tracking.service.impl;

import com.example.adplatform.admin.entity.BillingType;
import com.example.adplatform.infra.redis.budget.BudgetRedisService;
import com.example.adplatform.report.mapper.DailyReportMapper;
import com.example.adplatform.tracking.entity.ChargeRecordEntity;
import com.example.adplatform.tracking.entity.ChargeStatus;
import com.example.adplatform.tracking.entity.EventType;
import com.example.adplatform.tracking.mapper.ChargeRecordMapper;
import com.example.adplatform.tracking.message.EventMessage;
import com.example.adplatform.tracking.service.EventBillingProcessor;
import com.example.adplatform.tracking.service.EventContextResolver;
import com.example.adplatform.tracking.service.EventProcessingContext;
import com.example.adplatform.tracking.service.EventStatisticsStore;
import lombok.RequiredArgsConstructor;
import org.springframework.dao.DuplicateKeyException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/** 计费链路：独立判断预算并幂等写入 charge_record。 */
@RequiredArgsConstructor
@Service
public class EventBillingProcessorImpl implements EventBillingProcessor {

    private final EventContextResolver contextResolver;
    private final ChargeRecordMapper chargeRecordMapper;
    private final DailyReportMapper dailyReportMapper;
    private final BudgetRedisService budgetRedisService;
    private final EventStatisticsStore eventStatisticsStore;

    @Override
    @Transactional(rollbackFor = Exception.class)
    public void bill(EventMessage message) {
        EventProcessingContext context = contextResolver.resolve(message);
        long costAmount = calculateCostAmount(message, context);
        if (costAmount <= 0) {
            return;
        }

        ChargeRecordEntity charge = chargeRecordMapper.selectByEventId(message.eventId());
        if (charge == null) {
            boolean charged = budgetRedisService.tryChargeOnce(
                    message.eventId(), context.plan(), context.statDate(), costAmount);
            charge = toChargeRecord(
                    message,
                    context,
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
        recordCostStatistics(message.eventId(), context, charge);
    }

    private long calculateCostAmount(EventMessage message, EventProcessingContext context) {
        if (BillingType.CPC.name().equals(context.billingType())
                && message.eventType() == EventType.CLICK) {
            return context.plan().getBidPrice();
        }
        if (BillingType.CPA.name().equals(context.billingType())
                && message.eventType() == EventType.CONVERSION) {
            return context.plan().getBidPrice();
        }
        if (BillingType.CPM.name().equals(context.billingType())
                && message.eventType() == EventType.IMPRESSION) {
            long currentImpressions = dailyReportMapper.sumImpressionsByPlan(
                    context.statDate(), context.plan().getId());
            return (currentImpressions + 1) % 1000 == 0 ? context.plan().getBidPrice() : 0L;
        }
        return 0L;
    }

    private void recordCostStatistics(
            String eventId,
            EventProcessingContext context,
            ChargeRecordEntity charge) {
        boolean charged = ChargeStatus.SUCCESS.name().equals(charge.getChargeStatus());
        long finalCost = charged ? charge.getAmount() : 0L;
        eventStatisticsStore.recordCostOnce(eventId, context, finalCost);
    }

    private ChargeRecordEntity toChargeRecord(
            EventMessage message,
            EventProcessingContext context,
            long amount,
            ChargeStatus status) {
        ChargeRecordEntity record = new ChargeRecordEntity();
        record.setEventId(message.eventId());
        record.setPlanId(context.plan().getId());
        record.setMaterialId(context.material().getId());
        record.setSlotId(context.material().getSlotId());
        record.setBillingType(context.billingType());
        record.setAmount(amount);
        record.setChargeStatus(status.name());
        record.setChargeTime(context.eventTime());
        return record;
    }
}
