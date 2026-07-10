package com.example.adplatform.tracking.service.impl;

import com.example.adplatform.admin.entity.BillingType;
import com.example.adplatform.admin.entity.CampaignEntity;
import com.example.adplatform.admin.entity.CreativeEntity;
import com.example.adplatform.admin.mapper.CampaignMapper;
import com.example.adplatform.admin.mapper.CreativeMapper;
import com.example.adplatform.common.exception.BusinessException;
import com.example.adplatform.common.exception.ErrorCode;
import com.example.adplatform.report.mapper.AdStatsDailyMapper;
import com.example.adplatform.tracking.converter.AdEventConverter;
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

    private final AdEventMapper adEventMapper;
    private final AdStatsDailyMapper adStatsDailyMapper;
    private final CampaignMapper campaignMapper;
    private final CreativeMapper creativeMapper;
    private final AdEventConverter adEventConverter;

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
        // 根据计划计费方式判断本次事件是否应该产生扣费，再校验预算是否足够。
        long costAmount = calculateCost(campaign, billingType, eventType, statDate);
        boolean charged = costAmount > 0 && canCharge(campaign, statDate, costAmount);
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
            // event_id 有唯一索引，重复上报直接返回幂等结果，不重复累计统计和扣费。
            return duplicateResponse(request.eventId(), eventType);
        }

        // 写入原始事件成功后，同步累加日统计表，报表接口可以直接查询聚合结果。
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
        // CPC/CPA 是单事件扣费，CPM 是每满 1000 次曝光扣一次出价。
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
}
