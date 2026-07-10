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
import com.example.adplatform.delivery.converter.AdDeliveryConverter;
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
import java.util.ArrayList;
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
    private final AdDeliveryConverter adDeliveryConverter;

    @Override
    public AdDeliveryResponse deliver(AdDeliveryRequest request) {
        String requestId = "req_" + UUID.randomUUID().toString().replace("-", "");
        AdSlotEntity adSlot = adSlotMapper.selectOne(new LambdaQueryWrapper<AdSlotEntity>()
                .eq(AdSlotEntity::getSlotCode, request.slotCode())
                .eq(AdSlotEntity::getStatus, CommonStatus.ENABLED));
        if (adSlot == null) {
            throw new BusinessException(ErrorCode.RESOURCE_NOT_FOUND, "启用中的广告位不存在");
        }

        // 先按广告位召回可用素材，后续再逐个检查计划、预算、频控和定向。
        List<CreativeEntity> creatives = creativeMapper.selectList(new LambdaQueryWrapper<CreativeEntity>()
                .eq(CreativeEntity::getAdSlotId, adSlot.getId())
                .eq(CreativeEntity::getStatus, CommonStatus.ENABLED)
                .eq(CreativeEntity::getAuditStatus, CreativeAuditStatus.APPROVED.name())
                .orderByDesc(CreativeEntity::getId));

        int limit = request.size() == null ? DEFAULT_RETURN_SIZE : request.size();
        List<ScoredCreative> scoredCreatives = new ArrayList<>();
        LocalDate today = LocalDate.now();
        LocalDateTime now = LocalDateTime.now();

        for (CreativeEntity creative : creatives) {
            CampaignEntity campaign = campaignMapper.selectById(creative.getCampaignId());

            // 计划必须上线且处于投放时间内。
            if (campaign == null || !CampaignStatus.ONLINE.name().equals(campaign.getStatus())) {
                continue;
            }
            if (now.isBefore(campaign.getStartTime()) || now.isAfter(campaign.getEndTime())) {
                continue;
            }

            // 预算不足的广告不再返回，避免后续曝光/点击继续扩大消耗。
            long dailyCost = adStatsDailyMapper.sumCostByCampaignOnDate(today, campaign.getId());
            long totalCost = adStatsDailyMapper.sumCostByCampaign(campaign.getId());
            if (dailyCost >= campaign.getBudgetDaily() || totalCost >= campaign.getBudgetTotal()) {
                continue;
            }

            // 简化版用户频控：同一用户每天最多看到同一个计划 5 次。
            LocalDateTime dayStart = today.atStartOfDay();
            LocalDateTime dayEnd = dayStart.plusDays(1);
            long impressions = adEventMapper.countUserCampaignImpressions(
                    request.userId(),
                    campaign.getId(),
                    dayStart,
                    dayEnd);
            if (impressions >= MAX_FREQUENCY_PER_USER_DAY) {
                continue;
            }

            // 定向规则为空表示不限；存在规则时，地域、设备、性别、年龄、标签都要通过。
            TargetingRuleEntity rule = targetingRuleMapper.selectOne(new LambdaQueryWrapper<TargetingRuleEntity>()
                    .eq(TargetingRuleEntity::getCampaignId, campaign.getId()));
            if (rule != null) {
                boolean regionMatched = matchesList(rule.getRegion(), request.region());
                boolean deviceMatched = matchesList(rule.getDeviceType(), request.deviceType());
                boolean genderMatched = !StringUtils.hasText(rule.getGender())
                        || (StringUtils.hasText(request.gender()) && rule.getGender().equalsIgnoreCase(request.gender()));
                boolean ageMatched = true;
                if (rule.getAgeMin() != null || rule.getAgeMax() != null) {
                    ageMatched = request.age() != null
                            && (rule.getAgeMin() == null || request.age() >= rule.getAgeMin())
                            && (rule.getAgeMax() == null || request.age() <= rule.getAgeMax());
                }
                boolean tagsMatched = true;
                List<String> ruleTags = parseList(rule.getUserTags());
                if (!ruleTags.isEmpty()) {
                    tagsMatched = false;
                    if (request.tags() != null && !request.tags().isEmpty()) {
                        Set<String> normalizedRequestTags = new HashSet<>();
                        request.tags().forEach(tag -> normalizedRequestTags.add(tag.toLowerCase()));
                        tagsMatched = ruleTags.stream()
                                .map(String::toLowerCase)
                                .anyMatch(normalizedRequestTags::contains);
                    }
                }
                if (!regionMatched || !deviceMatched || !genderMatched || !ageMatched || !tagsMatched) {
                    continue;
                }
            }

            long campaignImpressions = adStatsDailyMapper.sumImpressionsByCampaign(today, campaign.getId());
            long campaignClicks = adStatsDailyMapper.sumClicksByCampaign(today, campaign.getId());
            double ctr = campaignImpressions <= 0
                    ? 0.02D
                    : Math.min((double) campaignClicks / campaignImpressions, 1D);
            // 简化版 eCPM 排序：出价权重最高，CTR 和默认质量分用于模拟广告效果因素。
            double score = campaign.getBidPrice() * 0.7 + ctr * 1000 * 0.2 + DEFAULT_QUALITY_SCORE * 0.1;
            scoredCreatives.add(new ScoredCreative(creative, campaign, score));
        }

        // 模拟广告系统的核心投放链路：召回候选 -> 过滤不可投广告 -> 计算分数 -> 返回 TopN。
        scoredCreatives = scoredCreatives.stream()
                .sorted(Comparator.comparingDouble(ScoredCreative::score).reversed())
                .limit(limit)
                .toList();

        List<AdItemVO> ads = scoredCreatives.stream()
                .map(item -> adDeliveryConverter.toAdItemVO(item.creative(), item.campaign(), item.score()))
                .toList();
        return new AdDeliveryResponse(requestId, creatives.size(), ads.size(), ads);
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

    private record ScoredCreative(
            CreativeEntity creative,
            CampaignEntity campaign,
            double score) {
    }
}
