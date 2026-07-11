package com.example.adplatform.report.service.impl;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.example.adplatform.common.exception.BusinessException;
import com.example.adplatform.common.exception.ErrorCode;
import com.example.adplatform.report.converter.ReportConverter;
import com.example.adplatform.report.entity.DailyReportEntity;
import com.example.adplatform.report.mapper.DailyReportMapper;
import com.example.adplatform.report.service.ReportService;
import com.example.adplatform.report.vo.DailyReportVO;
import com.example.adplatform.report.vo.FunnelStatsVO;
import com.example.adplatform.report.vo.TopMaterialVO;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

import java.time.LocalDate;
import java.util.Comparator;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

@RequiredArgsConstructor
@Service
public class ReportServiceImpl implements ReportService {

    private static final int DEFAULT_TOP_LIMIT = 10;
    private static final int MAX_TOP_LIMIT = 50;

    private final DailyReportMapper dailyReportMapper;
    private final ReportConverter reportConverter;

    @Override
    public List<DailyReportVO> daily(LocalDate statDate, Long planId) {
        LambdaQueryWrapper<DailyReportEntity> query = new LambdaQueryWrapper<DailyReportEntity>()
                .eq(DailyReportEntity::getStatDate, statDate)
                .eq(planId != null, DailyReportEntity::getPlanId, planId)
                .orderByDesc(DailyReportEntity::getCostAmount)
                .orderByDesc(DailyReportEntity::getClickCount);
        return dailyReportMapper.selectList(query).stream()
                .map(reportConverter::toDailyReportVO)
                .toList();
    }

    @Override
    public FunnelStatsVO funnel(LocalDate startDate, LocalDate endDate, Long planId) {
        if (endDate.isBefore(startDate)) {
            throw new BusinessException(ErrorCode.INVALID_TIME_RANGE, "结束日期不能早于开始日期");
        }
        LambdaQueryWrapper<DailyReportEntity> query = new LambdaQueryWrapper<DailyReportEntity>()
                .ge(DailyReportEntity::getStatDate, startDate)
                .le(DailyReportEntity::getStatDate, endDate)
                .eq(planId != null, DailyReportEntity::getPlanId, planId);
        List<DailyReportEntity> rows = dailyReportMapper.selectList(query);
        long impressions = rows.stream().mapToLong(DailyReportEntity::getImpressionCount).sum();
        long clicks = rows.stream().mapToLong(DailyReportEntity::getClickCount).sum();
        long conversions = rows.stream().mapToLong(DailyReportEntity::getConversionCount).sum();
        long costAmount = rows.stream().mapToLong(DailyReportEntity::getCostAmount).sum();
        return new FunnelStatsVO(
                startDate,
                endDate,
                planId,
                impressions,
                clicks,
                conversions,
                costAmount,
                divide(clicks, impressions),
                divide(conversions, clicks));
    }

    @Override
    public List<TopMaterialVO> topMaterials(LocalDate startDate, LocalDate endDate, Long planId, Integer limit) {
        if (endDate.isBefore(startDate)) {
            throw new BusinessException(ErrorCode.INVALID_TIME_RANGE, "结束日期不能早于开始日期");
        }
        int actualLimit = DEFAULT_TOP_LIMIT;
        if (limit != null && limit >= 1) {
            actualLimit = Math.min(limit, MAX_TOP_LIMIT);
        }
        LambdaQueryWrapper<DailyReportEntity> query = new LambdaQueryWrapper<DailyReportEntity>()
                .ge(DailyReportEntity::getStatDate, startDate)
                .le(DailyReportEntity::getStatDate, endDate)
                .eq(planId != null, DailyReportEntity::getPlanId, planId);
        Map<String, MaterialStatsAccumulator> accumulatorMap = new HashMap<>();
        dailyReportMapper.selectList(query).forEach(row -> {
            String key = row.getPlanId() + ":" + row.getMaterialId();
            MaterialStatsAccumulator accumulator = accumulatorMap.computeIfAbsent(
                    key,
                    ignored -> new MaterialStatsAccumulator(row.getPlanId(), row.getMaterialId(), row.getSlotId()));
            accumulator.add(row);
        });
        return accumulatorMap.values().stream()
                .map(MaterialStatsAccumulator::toVO)
                .sorted(Comparator.comparing(TopMaterialVO::costAmount).reversed()
                        .thenComparing(TopMaterialVO::clickCount, Comparator.reverseOrder())
                        .thenComparing(TopMaterialVO::impressionCount, Comparator.reverseOrder()))
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

        private TopMaterialVO toVO() {
            return new TopMaterialVO(
                    planId,
                    materialId,
                    slotId,
                    impressions,
                    clicks,
                    conversions,
                    costAmount,
                    divide(clicks, impressions),
                    divide(conversions, clicks));
        }
    }
}
