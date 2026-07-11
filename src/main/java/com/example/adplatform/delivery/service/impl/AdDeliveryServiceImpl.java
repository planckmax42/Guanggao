package com.example.adplatform.delivery.service.impl;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.example.adplatform.admin.entity.SlotEntity;
import com.example.adplatform.admin.entity.PlanEntity;
import com.example.adplatform.admin.entity.PlanStatus;
import com.example.adplatform.admin.entity.MaterialAuditStatus;
import com.example.adplatform.admin.entity.MaterialEntity;
import com.example.adplatform.admin.entity.RuleEntity;
import com.example.adplatform.admin.mapper.SlotMapper;
import com.example.adplatform.admin.mapper.PlanMapper;
import com.example.adplatform.admin.mapper.MaterialMapper;
import com.example.adplatform.admin.mapper.RuleMapper;
import com.example.adplatform.common.enums.CommonStatus;
import com.example.adplatform.common.exception.BusinessException;
import com.example.adplatform.common.exception.ErrorCode;
import com.example.adplatform.delivery.converter.AdDeliveryConverter;
import com.example.adplatform.delivery.dto.AdDeliveryRequest;
import com.example.adplatform.delivery.service.AdDeliveryService;
import com.example.adplatform.delivery.vo.AdDeliveryResponse;
import com.example.adplatform.delivery.vo.AdItemVO;
import com.example.adplatform.report.mapper.DailyReportMapper;
import com.example.adplatform.tracking.mapper.EventMapper;
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

    private final SlotMapper slotMapper;
    private final MaterialMapper materialMapper;
    private final PlanMapper planMapper;
    private final RuleMapper ruleMapper;
    private final DailyReportMapper dailyReportMapper;
    private final EventMapper eventMapper;
    private final ObjectMapper objectMapper;
    private final AdDeliveryConverter adDeliveryConverter;

    @Override
    public AdDeliveryResponse deliver(AdDeliveryRequest request) {
        String requestId = "req_" + UUID.randomUUID().toString().replace("-", "");
        SlotEntity slot = slotMapper.selectOne(new LambdaQueryWrapper<SlotEntity>()
                .eq(SlotEntity::getSlotCode, request.slotCode())
                .eq(SlotEntity::getStatus, CommonStatus.ENABLED));
        if (slot == null) {
            throw new BusinessException(ErrorCode.RESOURCE_NOT_FOUND, "启用中的广告位不存在");
        }

        // 先按广告位召回可用素材，后续再逐个检查计划、预算、频控和定向。
        List<MaterialEntity> materials = materialMapper.selectList(new LambdaQueryWrapper<MaterialEntity>()
                .eq(MaterialEntity::getSlotId, slot.getId())
                .eq(MaterialEntity::getStatus, CommonStatus.ENABLED)
                .eq(MaterialEntity::getAuditStatus, MaterialAuditStatus.APPROVED.name())
                .orderByDesc(MaterialEntity::getId));

        int limit = request.size() == null ? DEFAULT_RETURN_SIZE : request.size();
        List<ScoredMaterial> scoredMaterials = new ArrayList<>();
        LocalDate today = LocalDate.now();
        LocalDateTime now = LocalDateTime.now();

        for (MaterialEntity material : materials) {
            PlanEntity plan = planMapper.selectById(material.getPlanId());

            // 计划必须上线且处于投放时间内。
            if (plan == null || !PlanStatus.ONLINE.name().equals(plan.getStatus())) {
                continue;
            }
            if (now.isBefore(plan.getStartTime()) || now.isAfter(plan.getEndTime())) {
                continue;
            }

            // 预算不足的广告不再返回，避免后续曝光/点击继续扩大消耗。
            long dailyCost = dailyReportMapper.sumCostByPlanOnDate(today, plan.getId());
            long totalCost = dailyReportMapper.sumCostByPlan(plan.getId());
            if (dailyCost >= plan.getBudgetDaily() || totalCost >= plan.getBudgetTotal()) {
                continue;
            }

            // 简化版用户频控：同一用户每天最多看到同一个计划 5 次。
            LocalDateTime dayStart = today.atStartOfDay();
            LocalDateTime dayEnd = dayStart.plusDays(1);
            long impressions = eventMapper.countViewerPlanImpressions(
                    request.viewerId(),
                    plan.getId(),
                    dayStart,
                    dayEnd);
            if (impressions >= MAX_FREQUENCY_PER_USER_DAY) {
                continue;
            }

            // 定向规则为空表示不限；存在规则时，地域、设备、性别、年龄、标签都要通过。
            RuleEntity rule = ruleMapper.selectOne(new LambdaQueryWrapper<RuleEntity>()
                    .eq(RuleEntity::getPlanId, plan.getId()));
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

            long planImpressions = dailyReportMapper.sumImpressionsByPlan(today, plan.getId());
            long planClicks = dailyReportMapper.sumClicksByPlan(today, plan.getId());
            double ctr = planImpressions <= 0
                    ? 0.02D
                    : Math.min((double) planClicks / planImpressions, 1D);
            // 简化版 eCPM 排序：出价权重最高，CTR 和默认质量分用于模拟广告效果因素。
            double score = plan.getBidPrice() * 0.7 + ctr * 1000 * 0.2 + DEFAULT_QUALITY_SCORE * 0.1;
            scoredMaterials.add(new ScoredMaterial(material, plan, score));
        }

        // 模拟广告系统的核心投放链路：召回候选 -> 过滤不可投广告 -> 计算分数 -> 返回 TopN。
        scoredMaterials = scoredMaterials.stream()
                .sorted(Comparator.comparingDouble(ScoredMaterial::score).reversed())
                .limit(limit)
                .toList();

        List<AdItemVO> ads = scoredMaterials.stream()
                .map(item -> adDeliveryConverter.toAdItemVO(item.material(), item.plan(), item.score()))
                .toList();
        return new AdDeliveryResponse(requestId, materials.size(), ads.size(), ads);
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

    private record ScoredMaterial(
            MaterialEntity material,
            PlanEntity plan,
            double score) {
    }
}
