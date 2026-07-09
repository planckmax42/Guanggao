package com.example.adplatform.delivery.service.impl;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.example.adplatform.admin.entity.AdSlotEntity;
import com.example.adplatform.admin.entity.CampaignEntity;
import com.example.adplatform.admin.entity.CampaignStatus;
import com.example.adplatform.admin.entity.CreativeAuditStatus;
import com.example.adplatform.admin.entity.CreativeEntity;
import com.example.adplatform.admin.entity.TargetingRuleEntity;
import com.example.adplatform.admin.mapper.AdSlotMapper;
import com.example.adplatform.admin.mapper.CampaignMapper;
import com.example.adplatform.admin.mapper.CreativeMapper;
import com.example.adplatform.admin.mapper.TargetingRuleMapper;
import com.example.adplatform.common.enums.CommonStatus;
import com.example.adplatform.common.exception.BusinessException;
import com.example.adplatform.common.exception.ErrorCode;
import com.example.adplatform.delivery.dto.AdDeliveryRequest;
import com.example.adplatform.delivery.service.AdDeliveryService;
import com.example.adplatform.delivery.vo.AdDeliveryResponse;
import com.example.adplatform.delivery.vo.AdItemVO;
import com.example.adplatform.report.mapper.AdStatsDailyMapper;
import com.example.adplatform.tracking.mapper.AdEventMapper;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.Comparator;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.UUID;

@RequiredArgsConstructor
@Service
public class AdDeliveryServiceImpl implements AdDeliveryService {

    private static final TypeReference<List<String>> STRING_LIST_TYPE = new TypeReference<>() {
    };
    private static final int DEFAULT_RETURN_SIZE = 3;
    private static final int MAX_FREQUENCY_PER_USER_DAY = 5;
    private static final double DEFAULT_QUALITY_SCORE = 50D;

    private final AdSlotMapper adSlotMapper;
    private final CreativeMapper creativeMapper;
    private final CampaignMapper campaignMapper;
    private final TargetingRuleMapper targetingRuleMapper;
    private final AdStatsDailyMapper adStatsDailyMapper;
    private final AdEventMapper adEventMapper;
    private final ObjectMapper objectMapper;

    @Override
    public AdDeliveryResponse deliver(AdDeliveryRequest request) {
        String requestId = "req_" + UUID.randomUUID().toString().replace("-", "");
        AdSlotEntity adSlot = getEnabledAdSlot(request.slotCode());

        List<CreativeEntity> creatives = creativeMapper.selectList(new LambdaQueryWrapper<CreativeEntity>()
                .eq(CreativeEntity::getAdSlotId, adSlot.getId())
                .eq(CreativeEntity::getStatus, CommonStatus.ENABLED)
                .eq(CreativeEntity::getAuditStatus, CreativeAuditStatus.APPROVED.name())
                .orderByDesc(CreativeEntity::getId));

        int limit = request.size() == null ? DEFAULT_RETURN_SIZE : request.size();
        List<ScoredCreative> scoredCreatives = creatives.stream()
                .map(creative -> toScoredCreative(creative, request))
                .flatMap(List::stream)
                .sorted(Comparator.comparing(ScoredCreative::score).reversed())
                .limit(limit)
                .toList();

        List<AdItemVO> ads = scoredCreatives.stream()
                .map(this::toAdItemVO)
                .toList();
        return new AdDeliveryResponse(requestId, creatives.size(), ads.size(), ads);
    }

    private AdSlotEntity getEnabledAdSlot(String slotCode) {
        AdSlotEntity adSlot = adSlotMapper.selectOne(new LambdaQueryWrapper<AdSlotEntity>()
                .eq(AdSlotEntity::getSlotCode, slotCode)
                .eq(AdSlotEntity::getStatus, CommonStatus.ENABLED));
        if (adSlot == null) {
            throw new BusinessException(ErrorCode.RESOURCE_NOT_FOUND, "启用中的广告位不存在");
        }
        return adSlot;
    }

    private List<ScoredCreative> toScoredCreative(CreativeEntity creative, AdDeliveryRequest request) {
        CampaignEntity campaign = campaignMapper.selectById(creative.getCampaignId());
        if (!isCampaignDeliverable(campaign)) {
            return List.of();
        }
        if (isBudgetExceeded(campaign)) {
            return List.of();
        }
        if (isFrequencyExceeded(request.userId(), campaign.getId())) {
            return List.of();
        }
        TargetingRuleEntity rule = targetingRuleMapper.selectOne(new LambdaQueryWrapper<TargetingRuleEntity>()
                .eq(TargetingRuleEntity::getCampaignId, campaign.getId()));
        if (!matchesTargeting(rule, request)) {
            return List.of();
        }
        return List.of(new ScoredCreative(creative, campaign, calculateScore(campaign)));
    }

    private boolean isCampaignDeliverable(CampaignEntity campaign) {
        if (campaign == null || !CampaignStatus.ONLINE.name().equals(campaign.getStatus())) {
            return false;
        }
        LocalDateTime now = LocalDateTime.now();
        return !now.isBefore(campaign.getStartTime()) && !now.isAfter(campaign.getEndTime());
    }

    private boolean isBudgetExceeded(CampaignEntity campaign) {
        LocalDate today = LocalDate.now();
        long dailyCost = adStatsDailyMapper.sumCostByCampaignOnDate(today, campaign.getId());
        long totalCost = adStatsDailyMapper.sumCostByCampaign(campaign.getId());
        return dailyCost >= campaign.getBudgetDaily() || totalCost >= campaign.getBudgetTotal();
    }

    private boolean isFrequencyExceeded(Long userId, Long campaignId) {
        LocalDateTime startTime = LocalDate.now().atStartOfDay();
        LocalDateTime endTime = startTime.plusDays(1);
        long impressions = adEventMapper.countUserCampaignImpressions(userId, campaignId, startTime, endTime);
        return impressions >= MAX_FREQUENCY_PER_USER_DAY;
    }

    private boolean matchesTargeting(TargetingRuleEntity rule, AdDeliveryRequest request) {
        if (rule == null) {
            return true;
        }
        return matchesList(rule.getRegion(), request.region())
                && matchesList(rule.getDeviceType(), request.deviceType())
                && matchesGender(rule.getGender(), request.gender())
                && matchesAge(rule.getAgeMin(), rule.getAgeMax(), request.age())
                && matchesTags(rule.getUserTags(), request.tags());
    }

    private boolean matchesList(String ruleValue, String requestValue) {
        List<String> ruleItems = parseList(ruleValue);
        if (ruleItems.isEmpty()) {
            return true;
        }
        if (!StringUtils.hasText(requestValue)) {
            return false;
        }
        return ruleItems.stream().anyMatch(item -> item.equalsIgnoreCase(requestValue));
    }

    private boolean matchesGender(String ruleGender, String requestGender) {
        if (!StringUtils.hasText(ruleGender)) {
            return true;
        }
        return StringUtils.hasText(requestGender) && ruleGender.equalsIgnoreCase(requestGender);
    }

    private boolean matchesAge(Integer ageMin, Integer ageMax, Integer requestAge) {
        if (ageMin == null && ageMax == null) {
            return true;
        }
        if (requestAge == null) {
            return false;
        }
        return (ageMin == null || requestAge >= ageMin) && (ageMax == null || requestAge <= ageMax);
    }

    private boolean matchesTags(String ruleValue, List<String> requestTags) {
        List<String> ruleTags = parseList(ruleValue);
        if (ruleTags.isEmpty()) {
            return true;
        }
        if (requestTags == null || requestTags.isEmpty()) {
            return false;
        }
        Set<String> normalizedRequestTags = new HashSet<>();
        requestTags.forEach(tag -> normalizedRequestTags.add(tag.toLowerCase()));
        return ruleTags.stream()
                .map(String::toLowerCase)
                .anyMatch(normalizedRequestTags::contains);
    }

    private List<String> parseList(String value) {
        if (!StringUtils.hasText(value)) {
            return List.of();
        }
        try {
            return objectMapper.readValue(value, STRING_LIST_TYPE);
        } catch (JsonProcessingException ex) {
            throw new BusinessException(ErrorCode.BUSINESS_ERROR, "定向规则解析失败");
        }
    }

    private double calculateScore(CampaignEntity campaign) {
        double ctr = readCtr(campaign.getId());
        return campaign.getBidPrice() * 0.7 + ctr * 1000 * 0.2 + DEFAULT_QUALITY_SCORE * 0.1;
    }

    private double readCtr(Long campaignId) {
        LocalDate today = LocalDate.now();
        long impressions = adStatsDailyMapper.sumImpressionsByCampaign(today, campaignId);
        long clicks = adStatsDailyMapper.sumClicksByCampaign(today, campaignId);
        if (impressions <= 0) {
            return 0.02D;
        }
        return Math.min((double) clicks / impressions, 1D);
    }

    private AdItemVO toAdItemVO(ScoredCreative item) {
        CreativeEntity creative = item.creative();
        CampaignEntity campaign = item.campaign();
        return new AdItemVO(
                campaign.getId(),
                creative.getId(),
                creative.getAdSlotId(),
                creative.getTitle(),
                creative.getDescription(),
                creative.getImageUrl(),
                creative.getLandingPageUrl(),
                campaign.getBidPrice(),
                item.score());
    }

    private record ScoredCreative(
            CreativeEntity creative,
            CampaignEntity campaign,
            double score) {
    }
}
