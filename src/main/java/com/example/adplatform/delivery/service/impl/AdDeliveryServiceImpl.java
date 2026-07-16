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
        Long slotId = slotCacheService.getEnabledSlotIdByCode(request.slotCode())
                .orElseThrow(() -> new BusinessException(ErrorCode.RESOURCE_NOT_FOUND, "启用中的广告位不存在"));

        CandidateRecallResult recallResult = candidateRecallService.recall(request);
        List<AdCandidateDocument> recalled = recallResult.candidates();
        if (recalled.isEmpty()) {
            return new AdDeliveryResponse(requestId, 0, 0, List.of());
        }

        LocalDate today = LocalDate.now();
        Instant now = Instant.now();
        List<AdCandidateDocument> staticallyValid = recalled.stream()
                .filter(candidate -> Objects.equals(slotId, candidate.getSlotId()))
                .filter(candidate -> "ENABLED".equals(candidate.getMaterialStatus()))
                .filter(candidate -> "APPROVED".equals(candidate.getAuditStatus()))
                .filter(candidate -> PlanStatus.ONLINE.name().equals(candidate.getPlanStatus()))
                .filter(candidate -> candidate.getStartTime() != null && !now.isBefore(candidate.getStartTime()))
                .filter(candidate -> candidate.getEndTime() != null && !now.isAfter(candidate.getEndTime()))
                .filter(candidate -> CandidateTargetingMatcher.matches(candidate, request))
                .toList();

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
        double ctr = impressions <= 0 ? 0.02D : Math.min((double) clicks / impressions, 1D);
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
