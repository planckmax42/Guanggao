package com.example.adplatform.delivery.service.impl;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.example.adplatform.admin.entity.PlanEntity;
import com.example.adplatform.admin.entity.PlanStatus;
import com.example.adplatform.admin.entity.MaterialAuditStatus;
import com.example.adplatform.admin.entity.MaterialEntity;
import com.example.adplatform.admin.entity.RuleEntity;
import com.example.adplatform.admin.mapper.PlanMapper;
import com.example.adplatform.admin.mapper.MaterialMapper;
import com.example.adplatform.admin.dto.PlanRuleJoinRow;
import com.example.adplatform.common.enums.CommonStatus;
import com.example.adplatform.common.exception.BusinessException;
import com.example.adplatform.common.exception.ErrorCode;
import com.example.adplatform.delivery.converter.AdDeliveryConverter;
import com.example.adplatform.delivery.dto.AdDeliveryRequest;
import com.example.adplatform.delivery.service.AdDeliveryService;
import com.example.adplatform.delivery.vo.AdDeliveryResponse;
import com.example.adplatform.delivery.vo.AdItemVO;
import com.example.adplatform.infra.redis.budget.BudgetRedisService;
import com.example.adplatform.infra.redis.frequency.FrequencyRedisService;
import com.example.adplatform.infra.redis.slot.SlotCacheService;
import com.example.adplatform.report.mapper.DailyReportMapper;
import com.example.adplatform.report.vo.PlanDailyMetricVO;
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
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.UUID;
import java.util.stream.Collectors;

@RequiredArgsConstructor
@Service
public class AdDeliveryServiceImpl implements AdDeliveryService {

    private static final TypeReference<List<String>> STRING_LIST_TYPE = new TypeReference<>() {
    };
    private static final int DEFAULT_RETURN_SIZE = 3;
    private static final int MAX_FREQUENCY_PER_USER_DAY = 5;
    private static final double DEFAULT_QUALITY_SCORE = 50D;

    private final MaterialMapper materialMapper;
    private final PlanMapper planMapper;
    private final DailyReportMapper dailyReportMapper;
    private final SlotCacheService slotCacheService;
    private final BudgetRedisService budgetRedisService;
    private final FrequencyRedisService frequencyRedisService;
    private final ObjectMapper objectMapper;
    private final AdDeliveryConverter adDeliveryConverter;

    // SQL 耗时来自 2026-07-12 Arthas 单次压测样本，总耗时 441.226ms，仅用于定位当前瓶颈。
    @Override
    public AdDeliveryResponse deliver(AdDeliveryRequest request) {
        String requestId = "req_" + UUID.randomUUID().toString().replace("-", "");
        Long slotId = slotCacheService.getEnabledSlotIdByCode(request.slotCode()) // 查询广告位 ID：优先 Redis；未命中或 Redis 异常时回源 MySQL，原 MySQL 样本 33.171ms，占 7.52%。
                .orElseThrow(() -> new BusinessException(ErrorCode.RESOURCE_NOT_FOUND, "启用中的广告位不存在"));

        // 先按广告位召回可用素材，后续再逐个检查计划、预算、频控和定向。
        List<MaterialEntity> materials = materialMapper.selectList(new LambdaQueryWrapper<MaterialEntity>() // 查询候选素材：17.342ms，占 3.93%。
                .eq(MaterialEntity::getSlotId, slotId)
                .eq(MaterialEntity::getStatus, CommonStatus.ENABLED)
                .eq(MaterialEntity::getAuditStatus, MaterialAuditStatus.APPROVED.name())
                .orderByDesc(MaterialEntity::getId));

        if (materials.isEmpty()) {
            return new AdDeliveryResponse(requestId, 0, 0, List.of());
        }

        List<Long> planIds = materials.stream()
                .map(MaterialEntity::getPlanId)
                .filter(Objects::nonNull)
                .distinct()
                .toList();
        if (planIds.isEmpty()) {
            return new AdDeliveryResponse(requestId, materials.size(), 0, List.of());
        }

        List<PlanRuleJoinRow> planRuleRows = planMapper.selectPlanRuleRows(planIds);
        Map<Long, PlanEntity> planMap = planRuleRows.stream()
                .map(this::toPlanEntity)
                .collect(Collectors.toMap(PlanEntity::getId, plan -> plan));
        Map<Long, RuleEntity> ruleMap = planRuleRows.stream()
                .filter(row -> row.getRuleId() != null)
                .map(this::toRuleEntity)
                .collect(Collectors.toMap(RuleEntity::getPlanId, rule -> rule));

        int limit = request.size() == null ? DEFAULT_RETURN_SIZE : request.size();
        List<ScoredMaterial> scoredMaterials = new ArrayList<>();
        LocalDate today = LocalDate.now();
        LocalDateTime now = LocalDateTime.now();
        Map<Long, PlanDailyMetricVO> metricMap = dailyReportMapper.selectPlanDailyMetrics(today, planIds).stream()
                .collect(Collectors.toMap(PlanDailyMetricVO::getPlanId, metric -> metric));

        for (MaterialEntity material : materials) {
            PlanEntity plan = planMap.get(material.getPlanId()); // 批量查询计划后内存匹配，替代循环 selectById。

            // 计划必须上线且处于投放时间内。
            if (plan == null || !PlanStatus.ONLINE.name().equals(plan.getStatus())) {
                continue;
            }
            if (now.isBefore(plan.getStartTime()) || now.isAfter(plan.getEndTime())) {
                continue;
            }

            // 预算不足的广告不再返回，避免后续曝光/点击继续扩大消耗。
            if (!budgetRedisService.hasAvailableBudget(plan, today)) { // Redis 预算粗过滤：替代 daily_report 消耗查询，原 SQL 样本合计 52.634ms。
                continue;
            }

            // 简化版用户频控：同一用户每天最多看到同一个计划 5 次。
            if (frequencyRedisService.isViewerPlanFrequencyExceeded( // Redis 频控：替代 event 表曝光次数查询，原 SQL 样本 85.650ms，占 19.41%。
                    request.viewerId(),
                    plan.getId(),
                    today,
                    MAX_FREQUENCY_PER_USER_DAY)) {
                continue;
            }

            // 定向规则为空表示不限；存在规则时，地域、设备、性别、年龄、标签都要通过。
            RuleEntity rule = ruleMap.get(plan.getId()); // 批量查询规则后内存匹配，替代循环 selectOne。
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

            PlanDailyMetricVO metric = metricMap.get(plan.getId()); // 批量聚合日报后内存匹配，替代循环统计曝光/点击。
            long planImpressions = metric == null || metric.getImpressionCount() == null ? 0L : metric.getImpressionCount();
            long planClicks = metric == null || metric.getClickCount() == null ? 0L : metric.getClickCount();
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

    private PlanEntity toPlanEntity(PlanRuleJoinRow row) {
        PlanEntity plan = new PlanEntity();
        plan.setId(row.getPlanId());
        plan.setUserId(row.getUserId());
        plan.setName(row.getPlanName());
        plan.setBudgetTotal(row.getBudgetTotal());
        plan.setBudgetDaily(row.getBudgetDaily());
        plan.setBidPrice(row.getBidPrice());
        plan.setBillingType(row.getBillingType());
        plan.setStartTime(row.getStartTime());
        plan.setEndTime(row.getEndTime());
        plan.setStatus(row.getPlanStatus());
        plan.setCreatedAt(row.getPlanCreatedAt());
        plan.setUpdatedAt(row.getPlanUpdatedAt());
        return plan;
    }

    private RuleEntity toRuleEntity(PlanRuleJoinRow row) {
        RuleEntity rule = new RuleEntity();
        rule.setId(row.getRuleId());
        rule.setPlanId(row.getPlanId());
        rule.setRegion(row.getRegion());
        rule.setDeviceType(row.getDeviceType());
        rule.setGender(row.getGender());
        rule.setAgeMin(row.getAgeMin());
        rule.setAgeMax(row.getAgeMax());
        rule.setUserTags(row.getUserTags());
        rule.setCreatedAt(row.getRuleCreatedAt());
        rule.setUpdatedAt(row.getRuleUpdatedAt());
        return rule;
    }

    private record ScoredMaterial(
            MaterialEntity material,
            PlanEntity plan,
            double score) {
    }
}
