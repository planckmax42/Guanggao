package com.example.adplatform.tracking.service.impl;

import com.example.adplatform.admin.entity.BillingType;
import com.example.adplatform.admin.entity.CampaignEntity;
import com.example.adplatform.admin.entity.CreativeEntity;
import com.example.adplatform.admin.mapper.CampaignMapper;
import com.example.adplatform.admin.mapper.CreativeMapper;
import com.example.adplatform.common.exception.BusinessException;
import com.example.adplatform.common.exception.ErrorCode;
import com.example.adplatform.report.mapper.AdStatsDailyMapper;
import com.example.adplatform.tracking.dto.AdEventRequest;
import com.example.adplatform.tracking.entity.AdEventEntity;
import com.example.adplatform.tracking.entity.AdEventType;
import com.example.adplatform.tracking.mapper.AdEventMapper;
import com.example.adplatform.tracking.service.AdEventService;
import com.example.adplatform.tracking.vo.AdEventResponse;
import lombok.RequiredArgsConstructor;
import org.springframework.dao.DuplicateKeyException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDate;
import java.time.LocalDateTime;

@RequiredArgsConstructor
@Service
public class AdEventServiceImpl implements AdEventService {

    private static final int CHARGED = 1;
    private static final int NOT_CHARGED = 0;

    private final AdEventMapper adEventMapper;
    private final AdStatsDailyMapper adStatsDailyMapper;
    private final CampaignMapper campaignMapper;
    private final CreativeMapper creativeMapper;

    @Override
    @Transactional(rollbackFor = Exception.class)
    public AdEventResponse collect(AdEventRequest request) {
        AdEventType eventType = AdEventType.parse(request.eventType());
        CreativeEntity creative = getCreativeOrThrow(request.creativeId());
        CampaignEntity campaign = getCampaignOrThrow(request.campaignId());
        ensureEventRelationValid(request, creative);

        LocalDateTime eventTime = request.eventTime() == null ? LocalDateTime.now() : request.eventTime();
        LocalDate statDate = eventTime.toLocalDate();
        String billingType = BillingType.normalizeOrDefault(campaign.getBillingType());
        long costAmount = calculateCost(campaign, billingType, eventType, statDate);
        boolean charged = costAmount > 0 && canCharge(campaign, statDate, costAmount);
        long finalCostAmount = charged ? costAmount : 0L;

        AdEventEntity event = buildEvent(request, eventType, creative, billingType, charged, finalCostAmount, eventTime);
        try {
            adEventMapper.insert(event);
        } catch (DuplicateKeyException ex) {
            return duplicateResponse(request.eventId(), eventType);
        }

        adStatsDailyMapper.upsertIncrement(
                statDate,
                campaign.getId(),
                creative.getId(),
                creative.getAdSlotId(),
                eventType == AdEventType.IMPRESSION ? 1 : 0,
                eventType == AdEventType.CLICK ? 1 : 0,
                eventType == AdEventType.CONVERSION ? 1 : 0,
                finalCostAmount);

        return new AdEventResponse(
                request.eventId(),
                eventType.name(),
                false,
                charged,
                finalCostAmount,
                billingType);
    }

    private AdEventResponse duplicateResponse(String eventId, AdEventType eventType) {
        return new AdEventResponse(eventId, eventType.name(), true, false, 0L, null);
    }

    private CreativeEntity getCreativeOrThrow(Long creativeId) {
        CreativeEntity creative = creativeMapper.selectById(creativeId);
        if (creative == null) {
            throw new BusinessException(ErrorCode.RESOURCE_NOT_FOUND, "广告素材不存在");
        }
        return creative;
    }

    private CampaignEntity getCampaignOrThrow(Long campaignId) {
        CampaignEntity campaign = campaignMapper.selectById(campaignId);
        if (campaign == null) {
            throw new BusinessException(ErrorCode.RESOURCE_NOT_FOUND, "广告计划不存在");
        }
        return campaign;
    }

    private void ensureEventRelationValid(AdEventRequest request, CreativeEntity creative) {
        if (!request.campaignId().equals(creative.getCampaignId())) {
            throw new BusinessException(ErrorCode.BUSINESS_ERROR, "广告素材不属于该广告计划");
        }
        if (request.adSlotId() != null && !request.adSlotId().equals(creative.getAdSlotId())) {
            throw new BusinessException(ErrorCode.BUSINESS_ERROR, "广告素材不属于该广告位");
        }
    }

    private long calculateCost(CampaignEntity campaign, String billingType, AdEventType eventType, LocalDate statDate) {
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

    private boolean canCharge(CampaignEntity campaign, LocalDate statDate, long costAmount) {
        long dailyCost = adStatsDailyMapper.sumCostByCampaignOnDate(statDate, campaign.getId());
        long totalCost = adStatsDailyMapper.sumCostByCampaign(campaign.getId());
        return dailyCost + costAmount <= campaign.getBudgetDaily()
                && totalCost + costAmount <= campaign.getBudgetTotal();
    }

    private AdEventEntity buildEvent(
            AdEventRequest request,
            AdEventType eventType,
            CreativeEntity creative,
            String billingType,
            boolean charged,
            long costAmount,
            LocalDateTime eventTime) {
        AdEventEntity event = new AdEventEntity();
        event.setEventId(request.eventId());
        event.setRequestId(request.requestId());
        event.setEventType(eventType.name());
        event.setCampaignId(request.campaignId());
        event.setCreativeId(request.creativeId());
        event.setAdSlotId(creative.getAdSlotId());
        event.setUserId(request.userId());
        event.setBillingType(billingType);
        event.setCharged(charged ? CHARGED : NOT_CHARGED);
        event.setCostAmount(costAmount);
        event.setEventTime(eventTime);
        return event;
    }
}
