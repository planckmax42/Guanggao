package com.example.adplatform.tracking.service.impl;

import com.example.adplatform.admin.entity.BillingType;
import com.example.adplatform.admin.entity.PlanEntity;
import com.example.adplatform.admin.entity.MaterialEntity;
import com.example.adplatform.admin.mapper.PlanMapper;
import com.example.adplatform.admin.mapper.MaterialMapper;
import com.example.adplatform.common.exception.BusinessException;
import com.example.adplatform.common.exception.ErrorCode;
import com.example.adplatform.infra.redis.budget.BudgetRedisService;
import com.example.adplatform.infra.redis.frequency.FrequencyRedisService;
import com.example.adplatform.report.mapper.DailyReportMapper;
import com.example.adplatform.report.service.DailyReportRedisService;
import com.example.adplatform.tracking.converter.EventConverter;
import com.example.adplatform.tracking.entity.ChargeRecordEntity;
import com.example.adplatform.tracking.entity.ChargeStatus;
import com.example.adplatform.tracking.entity.EventEntity;
import com.example.adplatform.tracking.entity.EventType;
import com.example.adplatform.tracking.mapper.ChargeRecordMapper;
import com.example.adplatform.tracking.mapper.EventMapper;
import com.example.adplatform.tracking.message.EventMessage;
import com.example.adplatform.tracking.service.EventProcessor;
import lombok.RequiredArgsConstructor;
import org.springframework.dao.DuplicateKeyException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDate;
import java.time.LocalDateTime;

@RequiredArgsConstructor
@Service
public class EventProcessorImpl implements EventProcessor {

    private final EventMapper eventMapper;
    private final ChargeRecordMapper chargeRecordMapper;
    private final DailyReportMapper dailyReportMapper;
    private final DailyReportRedisService dailyReportRedisService;
    private final BudgetRedisService budgetRedisService;
    private final FrequencyRedisService frequencyRedisService;
    private final PlanMapper planMapper;
    private final MaterialMapper materialMapper;
    private final EventConverter eventConverter;

    @Override
    @Transactional(rollbackFor = Exception.class)
    public void process(EventMessage message) {
        EventType eventType = EventType.parse(message.eventType());
        MaterialEntity material = materialMapper.selectById(message.materialId());
        if (material == null) {
            throw new BusinessException(ErrorCode.RESOURCE_NOT_FOUND, "广告素材不存在");
        }
        PlanEntity plan = planMapper.selectById(material.getPlanId());
        if (plan == null) {
            throw new BusinessException(ErrorCode.RESOURCE_NOT_FOUND, "广告计划不存在");
        }

        LocalDateTime eventTime = message.eventTime() == null ? LocalDateTime.now() : message.eventTime();
        LocalDate statDate = eventTime.toLocalDate();
        String billingType = BillingType.normalizeOrDefault(plan.getBillingType());
        long costAmount = calculateCostAmount(eventType, plan, statDate);

        EventEntity event = eventConverter.toEntity(
                message,
                eventType,
                material,
                billingType,
                false,
                0L,
                eventTime);
        try {
            eventMapper.insert(event);
        } catch (DuplicateKeyException ex) {
            // event_id 有唯一索引，重复消息直接跳过，避免重复累计统计和扣费。
            return;
        }

        boolean charged = costAmount > 0 && budgetRedisService.tryCharge(plan, statDate, costAmount);
        long finalCostAmount = charged ? costAmount : 0L;
        if (costAmount > 0) {
            chargeRecordMapper.insert(toChargeRecord(
                    message,
                    material,
                    billingType,
                    finalCostAmount,
                    charged ? ChargeStatus.SUCCESS : ChargeStatus.BUDGET_EXHAUSTED,
                    eventTime));
            eventMapper.updateChargeResult(message.eventId(), charged ? 1 : 0, finalCostAmount);
        }

        if (eventType == EventType.IMPRESSION) {
            frequencyRedisService.incrementViewerPlanImpression(message.viewerId(), plan.getId(), statDate);
        }

        dailyReportRedisService.incrementDailyReport(
                statDate,
                plan.getId(),
                material.getId(),
                material.getSlotId(),
                eventType == EventType.IMPRESSION ? 1 : 0,
                eventType == EventType.CLICK ? 1 : 0,
                eventType == EventType.CONVERSION ? 1 : 0,
                finalCostAmount);
    }

    private long calculateCostAmount(EventType eventType, PlanEntity plan, LocalDate statDate) {
        String billingType = BillingType.normalizeOrDefault(plan.getBillingType());
        if (BillingType.CPC.name().equals(billingType) && eventType == EventType.CLICK) {
            return plan.getBidPrice();
        }
        if (BillingType.CPA.name().equals(billingType) && eventType == EventType.CONVERSION) {
            return plan.getBidPrice();
        }
        if (BillingType.CPM.name().equals(billingType) && eventType == EventType.IMPRESSION) {
            long currentImpressions = dailyReportMapper.sumImpressionsByPlan(statDate, plan.getId());
            return (currentImpressions + 1) % 1000 == 0 ? plan.getBidPrice() : 0L;
        }
        return 0L;
    }

    private ChargeRecordEntity toChargeRecord(
            EventMessage message,
            MaterialEntity material,
            String billingType,
            long amount,
            ChargeStatus chargeStatus,
            LocalDateTime chargeTime) {
        ChargeRecordEntity record = new ChargeRecordEntity();
        record.setEventId(message.eventId());
        record.setPlanId(material.getPlanId());
        record.setMaterialId(material.getId());
        record.setSlotId(material.getSlotId());
        record.setBillingType(billingType);
        record.setAmount(amount);
        record.setChargeStatus(chargeStatus.name());
        record.setChargeTime(chargeTime);
        return record;
    }
}
