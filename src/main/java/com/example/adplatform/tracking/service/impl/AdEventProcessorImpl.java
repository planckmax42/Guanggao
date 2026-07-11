package com.example.adplatform.tracking.service.impl;

import com.example.adplatform.admin.entity.BillingType;
import com.example.adplatform.admin.entity.CampaignEntity;
import com.example.adplatform.admin.entity.CreativeEntity;
import com.example.adplatform.admin.mapper.CampaignMapper;
import com.example.adplatform.admin.mapper.CreativeMapper;
import com.example.adplatform.common.exception.BusinessException;
import com.example.adplatform.common.exception.ErrorCode;
import com.example.adplatform.report.mapper.AdStatsDailyMapper;
import com.example.adplatform.report.service.AdStatsRedisService;
import com.example.adplatform.tracking.converter.AdEventConverter;
import com.example.adplatform.tracking.dto.AdEventRequest;
import com.example.adplatform.tracking.entity.AdEventEntity;
import com.example.adplatform.tracking.entity.AdEventType;
import com.example.adplatform.tracking.mapper.AdEventMapper;
import com.example.adplatform.tracking.message.AdEventMessage;
import com.example.adplatform.tracking.service.AdEventProcessor;
import lombok.RequiredArgsConstructor;
import org.springframework.dao.DuplicateKeyException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDate;
import java.time.LocalDateTime;

@RequiredArgsConstructor
@Service
public class AdEventProcessorImpl implements AdEventProcessor {

    private final AdEventMapper adEventMapper;
    private final AdStatsDailyMapper adStatsDailyMapper;
    private final AdStatsRedisService adStatsRedisService;
    private final CampaignMapper campaignMapper;
    private final CreativeMapper creativeMapper;
    private final AdEventConverter adEventConverter;

    @Override
    @Transactional(rollbackFor = Exception.class)
    public void process(AdEventMessage message) {
        AdEventRequest request = message.toRequest();
        AdEventType eventType = AdEventType.parse(request.eventType());
        CreativeEntity creative = creativeMapper.selectById(request.creativeId());
        if (creative == null) {
            throw new BusinessException(ErrorCode.RESOURCE_NOT_FOUND, "广告素材不存在");
        }
        CampaignEntity campaign = campaignMapper.selectById(creative.getCampaignId());
        if (campaign == null) {
            throw new BusinessException(ErrorCode.RESOURCE_NOT_FOUND, "广告计划不存在");
        }

        LocalDateTime eventTime = request.eventTime() == null ? LocalDateTime.now() : request.eventTime();
        LocalDate statDate = eventTime.toLocalDate();
        String billingType = BillingType.normalizeOrDefault(campaign.getBillingType());
        long costAmount = calculateCostAmount(eventType, campaign, statDate);

        long dailyCost = adStatsDailyMapper.sumCostByCampaignOnDate(statDate, campaign.getId());
        long totalCost = adStatsDailyMapper.sumCostByCampaign(campaign.getId());
        boolean charged = costAmount > 0
                && dailyCost + costAmount <= campaign.getBudgetDaily()
                && totalCost + costAmount <= campaign.getBudgetTotal();
        long finalCostAmount = charged ? costAmount : 0L;

        AdEventEntity event = adEventConverter.toEntity(
                request,
                eventType,
                creative,
                billingType,
                charged,
                finalCostAmount,
                eventTime);
        try {
            adEventMapper.insert(event);
        } catch (DuplicateKeyException ex) {
            // event_id 有唯一索引，重复消息直接跳过，避免重复累计统计和扣费。
            return;
        }

        adStatsRedisService.incrementDailyStats(
                statDate,
                campaign.getId(),
                creative.getId(),
                creative.getAdSlotId(),
                eventType == AdEventType.IMPRESSION ? 1 : 0,
                eventType == AdEventType.CLICK ? 1 : 0,
                eventType == AdEventType.CONVERSION ? 1 : 0,
                finalCostAmount);
    }

    private long calculateCostAmount(AdEventType eventType, CampaignEntity campaign, LocalDate statDate) {
        String billingType = BillingType.normalizeOrDefault(campaign.getBillingType());
        if (BillingType.CPC.name().equals(billingType) && eventType == AdEventType.CLICK) {
            return campaign.getBidPrice();
        }
        if (BillingType.CPA.name().equals(billingType) && eventType == AdEventType.CONVERSION) {
            return campaign.getBidPrice();
        }
        if (BillingType.CPM.name().equals(billingType) && eventType == AdEventType.IMPRESSION) {
            long currentImpressions = adStatsDailyMapper.sumImpressionsByCampaign(statDate, campaign.getId());
            return (currentImpressions + 1) % 1000 == 0 ? campaign.getBidPrice() : 0L;
        }
        return 0L;
    }
}
