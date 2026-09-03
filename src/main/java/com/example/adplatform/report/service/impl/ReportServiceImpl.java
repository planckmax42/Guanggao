package com.example.adplatform.report.service.impl;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.example.adplatform.common.exception.BusinessException;
import com.example.adplatform.common.exception.ErrorCode;
import com.example.adplatform.report.converter.ReportConverter;
import com.example.adplatform.report.entity.DailyReportEntity;
import com.example.adplatform.report.mapper.DailyReportMapper;
import com.example.adplatform.report.service.ReportService;
import com.example.adplatform.report.response.DailyReportResponse;
import com.example.adplatform.report.response.FunnelStatsResponse;
import com.example.adplatform.report.response.TopMaterialResponse;
import com.example.adplatform.admin.entity.MaterialEntity;
import com.example.adplatform.admin.entity.PlanEntity;
import com.example.adplatform.admin.entity.SlotEntity;
import com.example.adplatform.admin.mapper.MaterialMapper;
import com.example.adplatform.admin.mapper.PlanMapper;
import com.example.adplatform.admin.mapper.SlotMapper;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

import java.time.LocalDate;
import java.util.Comparator;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Collection;
import java.util.stream.Collectors;

@RequiredArgsConstructor
@Service
public class ReportServiceImpl implements ReportService {

    private static final int DEFAULT_TOP_LIMIT = 10;
    private static final int MAX_TOP_LIMIT = 50;

    private final DailyReportMapper dailyReportMapper;
    private final ReportConverter reportConverter;
    private final PlanMapper planMapper;
    private final MaterialMapper materialMapper;
    private final SlotMapper slotMapper;

    @Override
    public List<DailyReportResponse> daily(LocalDate statDate, String planPublicId) {
        Long planId = resolvePlanId(planPublicId);
        LambdaQueryWrapper<DailyReportEntity> query = new LambdaQueryWrapper<DailyReportEntity>()
                .eq(DailyReportEntity::getStatDate, statDate)
                .eq(planId != null, DailyReportEntity::getPlanId, planId)
                .orderByDesc(DailyReportEntity::getCostAmount)
                .orderByDesc(DailyReportEntity::getClickCount);
        List<DailyReportEntity> rows = dailyReportMapper.selectList(query);
        ResourcePublicIds ids = loadPublicIds(rows);
        return rows.stream()
                .map(row -> reportConverter.toDailyReportResponse(
                        row,
                        ids.planIds().get(row.getPlanId()),
                        ids.materialIds().get(row.getMaterialId()),
                        ids.slotIds().get(row.getSlotId())))
                .toList();
    }

    @Override
    public FunnelStatsResponse funnel(LocalDate startDate, LocalDate endDate, String planPublicId) {
        if (endDate.isBefore(startDate)) {
            throw new BusinessException(ErrorCode.INVALID_TIME_RANGE, "结束日期不能早于开始日期");
        }
        Long planId = resolvePlanId(planPublicId);
        LambdaQueryWrapper<DailyReportEntity> query = new LambdaQueryWrapper<DailyReportEntity>()
                .ge(DailyReportEntity::getStatDate, startDate)
                .le(DailyReportEntity::getStatDate, endDate)
                .eq(planId != null, DailyReportEntity::getPlanId, planId);
        List<DailyReportEntity> rows = dailyReportMapper.selectList(query);
        long impressions = rows.stream().mapToLong(DailyReportEntity::getImpressionCount).sum();
        long clicks = rows.stream().mapToLong(DailyReportEntity::getClickCount).sum();
        long conversions = rows.stream().mapToLong(DailyReportEntity::getConversionCount).sum();
        long costAmount = rows.stream().mapToLong(DailyReportEntity::getCostAmount).sum();
        return new FunnelStatsResponse(
                startDate,
                endDate,
                planPublicId,
                impressions,
                clicks,
                conversions,
                costAmount,
                divide(clicks, impressions),
                divide(conversions, clicks));
    }

    @Override
    public List<TopMaterialResponse> topMaterials(
            LocalDate startDate,
            LocalDate endDate,
            String planPublicId,
            Integer limit) {
        if (endDate.isBefore(startDate)) {
            throw new BusinessException(ErrorCode.INVALID_TIME_RANGE, "结束日期不能早于开始日期");
        }
        Long planId = resolvePlanId(planPublicId);
        int actualLimit = DEFAULT_TOP_LIMIT;
        if (limit != null && limit >= 1) {
            actualLimit = Math.min(limit, MAX_TOP_LIMIT);
        }
        LambdaQueryWrapper<DailyReportEntity> query = new LambdaQueryWrapper<DailyReportEntity>()
                .ge(DailyReportEntity::getStatDate, startDate)
                .le(DailyReportEntity::getStatDate, endDate)
                .eq(planId != null, DailyReportEntity::getPlanId, planId);
        Map<String, MaterialStatsAccumulator> accumulatorMap = new HashMap<>();
        List<DailyReportEntity> rows = dailyReportMapper.selectList(query);
        ResourcePublicIds ids = loadPublicIds(rows);
        rows.forEach(row -> {
            String key = row.getPlanId() + ":" + row.getMaterialId();
            MaterialStatsAccumulator accumulator = accumulatorMap.computeIfAbsent(
                    key,
                    ignored -> new MaterialStatsAccumulator(row.getPlanId(), row.getMaterialId(), row.getSlotId()));
            accumulator.add(row);
        });
        return accumulatorMap.values().stream()
                .map(accumulator -> accumulator.toResponse(ids))
                .sorted(Comparator.comparing(TopMaterialResponse::costAmount).reversed()
                        .thenComparing(TopMaterialResponse::clickCount, Comparator.reverseOrder())
                        .thenComparing(TopMaterialResponse::impressionCount, Comparator.reverseOrder()))
                .limit(actualLimit)
                .toList();
    }

    private double divide(long numerator, long denominator) {
        if (denominator <= 0) {
            return 0D;
        }
        return (double) numerator / denominator;
    }

    private class MaterialStatsAccumulator {

        private final Long planId;
        private final Long materialId;
        private final Long slotId;
        private long impressions;
        private long clicks;
        private long conversions;
        private long costAmount;

        private MaterialStatsAccumulator(Long planId, Long materialId, Long slotId) {
            this.planId = planId;
            this.materialId = materialId;
            this.slotId = slotId;
        }

        private void add(DailyReportEntity row) {
            impressions += row.getImpressionCount();
            clicks += row.getClickCount();
            conversions += row.getConversionCount();
            costAmount += row.getCostAmount();
        }

        private TopMaterialResponse toResponse(ResourcePublicIds ids) {
            return new TopMaterialResponse(
                    ids.planIds().get(planId),
                    ids.materialIds().get(materialId),
                    ids.slotIds().get(slotId),
                    impressions,
                    clicks,
                    conversions,
                    costAmount,
                    divide(clicks, impressions),
                    divide(conversions, clicks));
        }
    }

    private Long resolvePlanId(String planPublicId) {
        if (planPublicId == null) {
            return null;
        }
        PlanEntity plan = planMapper.selectOne(new LambdaQueryWrapper<PlanEntity>()
                .eq(PlanEntity::getPublicId, planPublicId));
        if (plan == null) {
            throw new BusinessException(ErrorCode.RESOURCE_NOT_FOUND, "广告计划不存在");
        }
        return plan.getId();
    }

    private ResourcePublicIds loadPublicIds(List<DailyReportEntity> rows) {
        return new ResourcePublicIds(
                indexPlans(rows.stream().map(DailyReportEntity::getPlanId).toList()),
                indexMaterials(rows.stream().map(DailyReportEntity::getMaterialId).toList()),
                indexSlots(rows.stream().map(DailyReportEntity::getSlotId).toList()));
    }

    private Map<Long, String> indexPlans(Collection<Long> ids) {
        List<Long> distinct = ids.stream().distinct().toList();
        if (distinct.isEmpty()) {
            return Map.of();
        }
        return planMapper.selectBatchIds(distinct).stream()
                .collect(Collectors.toMap(PlanEntity::getId, PlanEntity::getPublicId));
    }

    private Map<Long, String> indexMaterials(Collection<Long> ids) {
        List<Long> distinct = ids.stream().distinct().toList();
        if (distinct.isEmpty()) {
            return Map.of();
        }
        return materialMapper.selectBatchIds(distinct).stream()
                .collect(Collectors.toMap(MaterialEntity::getId, MaterialEntity::getPublicId));
    }

    private Map<Long, String> indexSlots(Collection<Long> ids) {
        List<Long> distinct = ids.stream().distinct().toList();
        if (distinct.isEmpty()) {
            return Map.of();
        }
        return slotMapper.selectBatchIds(distinct).stream()
                .collect(Collectors.toMap(SlotEntity::getId, SlotEntity::getPublicId));
    }

    private record ResourcePublicIds(
            Map<Long, String> planIds,
            Map<Long, String> materialIds,
            Map<Long, String> slotIds) {
    }
}
