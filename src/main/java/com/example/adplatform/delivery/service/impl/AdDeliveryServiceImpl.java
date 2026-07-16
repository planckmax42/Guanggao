package com.example.adplatform.delivery.service.impl;

import com.example.adplatform.admin.entity.PlanEntity;
import com.example.adplatform.admin.entity.PlanStatus;
import com.example.adplatform.common.exception.BusinessException;
import com.example.adplatform.common.exception.ErrorCode;
import com.example.adplatform.delivery.dto.AdDeliveryRequest;
import com.example.adplatform.delivery.service.AdDeliveryService;
import com.example.adplatform.delivery.vo.AdDeliveryResponse;
import com.example.adplatform.delivery.vo.AdItemVO;
import com.example.adplatform.infra.redis.budget.BudgetRedisService;
import com.example.adplatform.infra.redis.frequency.FrequencyRedisService;
import com.example.adplatform.infra.redis.slot.SlotCacheService;
import com.example.adplatform.report.mapper.DailyReportMapper;
import com.example.adplatform.report.vo.PlanDailyMetricVO;
import com.example.adplatform.search.candidate.model.AdCandidateDocument;
import com.example.adplatform.search.candidate.service.CandidateRecallResult;
import com.example.adplatform.search.candidate.service.CandidateRecallService;
import com.example.adplatform.search.candidate.service.CandidateTargetingMatcher;
import com.example.adplatform.search.candidate.service.DeliveryStopGuardService;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

import java.time.Instant;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.UUID;
import java.util.function.Function;
import java.util.stream.Collectors;

/**
 * 第三阶段广告投放编排：ES 静态粗召回、Redis 动态过滤、Java 内存精排。
 *
 * <p>该类只负责在线投放路径的编排，不负责维护 ES 索引或消费曝光事件。候选快照中的
 * 定向、预算上限和出价来自 MySQL 配置同步；实时停投、预算消耗和用户频控由 Redis
 * 在请求时校验。ES 发生异常时由 {@link CandidateRecallService} 统一降级到 MySQL。</p>
 */
@RequiredArgsConstructor
@Service
public class AdDeliveryServiceImpl implements AdDeliveryService {

    private static final int DEFAULT_RETURN_SIZE = 3;
    private static final int MAX_FREQUENCY_PER_USER_DAY = 5;
    private static final double DEFAULT_QUALITY_SCORE = 50D;

    private final SlotCacheService slotCacheService;
    private final CandidateRecallService candidateRecallService;
    private final DeliveryStopGuardService stopGuardService;
    private final BudgetRedisService budgetRedisService;
    private final FrequencyRedisService frequencyRedisService;
    private final DailyReportMapper dailyReportMapper;

    @Override
    public AdDeliveryResponse deliver(AdDeliveryRequest request) {
        String requestId = "req_" + UUID.randomUUID().toString().replace("-", "");

        // 先通过广告位缓存校验入口有效性，避免无效广告位请求继续访问 ES。
        Long slotId = slotCacheService.getEnabledSlotIdByCode(request.slotCode())
                .orElseThrow(() -> new BusinessException(ErrorCode.RESOURCE_NOT_FOUND, "启用中的广告位不存在"));

        // 第一阶段：ES 多维粗召回。只有异常/超时/熔断才回源，合法空结果不会查询 MySQL。
        CandidateRecallResult recallResult = candidateRecallService.recall(request);
        List<AdCandidateDocument> recalled = recallResult.candidates();
        if (recalled.isEmpty()) {
            return new AdDeliveryResponse(requestId, 0, 0, List.of());
        }

        LocalDate today = LocalDate.now();
        Instant now = Instant.now();

        // 第二阶段（静态防御校验）：防止索引最终一致窗口或脏数据导致不合规候选进入排序。
        List<AdCandidateDocument> staticallyValid = recalled.stream()
                .filter(candidate -> Objects.equals(slotId, candidate.getSlotId()))
                .filter(candidate -> "ENABLED".equals(candidate.getMaterialStatus()))
                .filter(candidate -> "APPROVED".equals(candidate.getAuditStatus()))
                .filter(candidate -> PlanStatus.ONLINE.name().equals(candidate.getPlanStatus()))
                .filter(candidate -> candidate.getStartTime() != null && !now.isBefore(candidate.getStartTime()))
                .filter(candidate -> candidate.getEndTime() != null && !now.isAfter(candidate.getEndTime()))
                .filter(candidate -> CandidateTargetingMatcher.matches(candidate, request))
                .toList();

        // 紧急停投集合覆盖 ES 的短暂同步窗口；一次 pipeline 批量查询，避免逐候选访问 Redis。
        Set<Long> stoppedPlans = stopGuardService.findStoppedPlans(
                staticallyValid.stream().map(AdCandidateDocument::getPlanId).distinct().toList());
        Set<Long> stoppedMaterials = stopGuardService.findStoppedMaterials(
                staticallyValid.stream().map(AdCandidateDocument::getMaterialId).toList());
        Set<Long> stoppedSlots = stopGuardService.findStoppedSlots(List.of(slotId));
        List<AdCandidateDocument> guarded = staticallyValid.stream()
                .filter(candidate -> !stoppedPlans.contains(candidate.getPlanId()))
                .filter(candidate -> !stoppedMaterials.contains(candidate.getMaterialId()))
                .filter(candidate -> !stoppedSlots.contains(candidate.getSlotId()))
                .toList();
        if (guarded.isEmpty()) {
            return new AdDeliveryResponse(requestId, recalled.size(), 0, List.of());
        }

        // 预算和频控属于高频动态状态，不进入 ES，分别使用 Redis multiGet 批量过滤。
        Map<Long, PlanEntity> planMap = guarded.stream()
                .collect(Collectors.toMap(
                        AdCandidateDocument::getPlanId,
                        this::toPlanEntity,
                        (first, ignored) -> first));
        Set<Long> unavailablePlans = budgetRedisService.findUnavailablePlans(planMap.values(), today);
        Set<Long> frequencyExceededPlans = frequencyRedisService.findExceededPlans(
                request.viewerId(), planMap.keySet(), today, MAX_FREQUENCY_PER_USER_DAY);
        List<AdCandidateDocument> dynamicallyValid = guarded.stream()
                .filter(candidate -> !unavailablePlans.contains(candidate.getPlanId()))
                .filter(candidate -> !frequencyExceededPlans.contains(candidate.getPlanId()))
                .toList();
        if (dynamicallyValid.isEmpty()) {
            return new AdDeliveryResponse(requestId, recalled.size(), 0, List.of());
        }

        // 第三阶段：批量读取计划当日指标，在 JVM 内完成 CTR、出价和质量分精排。
        List<Long> planIds = dynamicallyValid.stream()
                .map(AdCandidateDocument::getPlanId)
                .distinct()
                .toList();
        Map<Long, PlanDailyMetricVO> metricMap = dailyReportMapper.selectPlanDailyMetrics(today, planIds).stream()
                .collect(Collectors.toMap(PlanDailyMetricVO::getPlanId, Function.identity()));

        int limit = request.size() == null ? DEFAULT_RETURN_SIZE : request.size();
        List<AdItemVO> ads = dynamicallyValid.stream()
                .map(candidate -> score(candidate, metricMap.get(candidate.getPlanId())))
                .sorted(Comparator.comparingDouble(ScoredCandidate::score).reversed()
                        .thenComparing(item -> item.candidate().getMaterialId(), Comparator.reverseOrder()))
                .limit(limit)
                .map(this::toAdItemVO)
                .toList();
        return new AdDeliveryResponse(requestId, recalled.size(), ads.size(), ads);
    }

    private ScoredCandidate score(AdCandidateDocument candidate, PlanDailyMetricVO metric) {
        long impressions = metric == null || metric.getImpressionCount() == null ? 0L : metric.getImpressionCount();
        long clicks = metric == null || metric.getClickCount() == null ? 0L : metric.getClickCount();
        // 冷启动候选使用 2% 先验 CTR，避免零曝光素材永远排不到前面。
        double ctr = impressions <= 0 ? 0.02D : Math.min((double) clicks / impressions, 1D);
        // 示例精排公式：70% 出价 + 20% CTR 价值 + 10% 固定质量分。
        double value = candidate.getBidPrice() * 0.7 + ctr * 1000 * 0.2 + DEFAULT_QUALITY_SCORE * 0.1;
        return new ScoredCandidate(candidate, value);
    }

    private AdItemVO toAdItemVO(ScoredCandidate item) {
        AdCandidateDocument candidate = item.candidate();
        return new AdItemVO(
                candidate.getPlanId(),
                candidate.getMaterialId(),
                candidate.getSlotId(),
                candidate.getTitle(),
                candidate.getDescription(),
                candidate.getImageUrl(),
                candidate.getLandingPageUrl(),
                candidate.getBidPrice(),
                item.score());
    }

    private PlanEntity toPlanEntity(AdCandidateDocument candidate) {
        PlanEntity plan = new PlanEntity();
        plan.setId(candidate.getPlanId());
        plan.setUserId(candidate.getUserId());
        plan.setBudgetTotal(candidate.getBudgetTotal());
        plan.setBudgetDaily(candidate.getBudgetDaily());
        plan.setBidPrice(candidate.getBidPrice());
        plan.setBillingType(candidate.getBillingType());
        plan.setStartTime(toLocalDateTime(candidate.getStartTime()));
        plan.setEndTime(toLocalDateTime(candidate.getEndTime()));
        plan.setStatus(candidate.getPlanStatus());
        return plan;
    }

    private LocalDateTime toLocalDateTime(Instant value) {
        return value == null ? null : LocalDateTime.ofInstant(value, ZoneId.systemDefault());
    }

    private record ScoredCandidate(AdCandidateDocument candidate, double score) { }
}
