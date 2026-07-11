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
    private final AdStatsRedisService adStatsRedisService;
    private final CampaignMapper campaignMapper;
    private final CreativeMapper creativeMapper;
    private final AdEventConverter adEventConverter;

    @Override
    @Transactional
    public AdEventResponse collect(AdEventRequest request) {
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
        // 根据计划计费方式判断本次事件是否应该产生扣费，再校验预算是否足够。
        long costAmount = 0L;
        if (BillingType.CPC.name().equals(billingType) && eventType == AdEventType.CLICK) {
            costAmount = campaign.getBidPrice();
        } else if (BillingType.CPA.name().equals(billingType) && eventType == AdEventType.CONVERSION) {
            costAmount = campaign.getBidPrice();
        } else if (BillingType.CPM.name().equals(billingType) && eventType == AdEventType.IMPRESSION) {
            long currentImpressions = adStatsDailyMapper.sumImpressionsByCampaign(statDate, campaign.getId());
            costAmount = (currentImpressions + 1) % 1000 == 0 ? campaign.getBidPrice() : 0L;
        }

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
            // event_id 有唯一索引，重复上报直接返回幂等结果，不重复累计统计和扣费。
            return new AdEventResponse(request.eventId(), eventType.name(), true, false, 0L, null);
        }

        // 写入原始事件成功后，先累加 Redis 实时统计；定时任务再批量刷入 MySQL 日统计表。
        adStatsRedisService.incrementDailyStats(
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
}
