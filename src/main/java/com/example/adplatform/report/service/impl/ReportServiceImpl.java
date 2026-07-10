package com.example.adplatform.report.service.impl;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.example.adplatform.common.exception.BusinessException;
import com.example.adplatform.common.exception.ErrorCode;
import com.example.adplatform.report.converter.ReportConverter;
import com.example.adplatform.report.entity.AdStatsDailyEntity;
import com.example.adplatform.report.mapper.AdStatsDailyMapper;
import com.example.adplatform.report.service.ReportService;
import com.example.adplatform.report.vo.DailyStatsVO;
import com.example.adplatform.report.vo.FunnelStatsVO;
import com.example.adplatform.report.vo.TopCreativeVO;
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

    private final AdStatsDailyMapper adStatsDailyMapper;
    private final ReportConverter reportConverter;

    @Override
    public List<DailyStatsVO> daily(LocalDate statDate, Long campaignId) {
        LambdaQueryWrapper<AdStatsDailyEntity> query = new LambdaQueryWrapper<AdStatsDailyEntity>()
                .eq(AdStatsDailyEntity::getStatDate, statDate)
                .eq(campaignId != null, AdStatsDailyEntity::getCampaignId, campaignId)
                .orderByDesc(AdStatsDailyEntity::getCostAmount)
                .orderByDesc(AdStatsDailyEntity::getClickCount);
        return adStatsDailyMapper.selectList(query).stream()
                .map(reportConverter::toDailyStatsVO)
                .toList();
    }

    @Override
    public FunnelStatsVO funnel(LocalDate startDate, LocalDate endDate, Long campaignId) {
        if (endDate.isBefore(startDate)) {
            throw new BusinessException(ErrorCode.INVALID_TIME_RANGE, "结束日期不能早于开始日期");
        }
        LambdaQueryWrapper<AdStatsDailyEntity> query = new LambdaQueryWrapper<AdStatsDailyEntity>()
                .ge(AdStatsDailyEntity::getStatDate, startDate)
                .le(AdStatsDailyEntity::getStatDate, endDate)
                .eq(campaignId != null, AdStatsDailyEntity::getCampaignId, campaignId);
        List<AdStatsDailyEntity> rows = adStatsDailyMapper.selectList(query);
        long impressions = rows.stream().mapToLong(AdStatsDailyEntity::getImpressionCount).sum();
        long clicks = rows.stream().mapToLong(AdStatsDailyEntity::getClickCount).sum();
        long conversions = rows.stream().mapToLong(AdStatsDailyEntity::getConversionCount).sum();
        long costAmount = rows.stream().mapToLong(AdStatsDailyEntity::getCostAmount).sum();
        return new FunnelStatsVO(
                startDate,
                endDate,
                campaignId,
                impressions,
                clicks,
                conversions,
                costAmount,
                divide(clicks, impressions),
                divide(conversions, clicks));
    }

    @Override
    public List<TopCreativeVO> topCreatives(LocalDate startDate, LocalDate endDate, Long campaignId, Integer limit) {
        if (endDate.isBefore(startDate)) {
            throw new BusinessException(ErrorCode.INVALID_TIME_RANGE, "结束日期不能早于开始日期");
        }
        int actualLimit = DEFAULT_TOP_LIMIT;
        if (limit != null && limit >= 1) {
            actualLimit = Math.min(limit, MAX_TOP_LIMIT);
        }
        LambdaQueryWrapper<AdStatsDailyEntity> query = new LambdaQueryWrapper<AdStatsDailyEntity>()
                .ge(AdStatsDailyEntity::getStatDate, startDate)
                .le(AdStatsDailyEntity::getStatDate, endDate)
                .eq(campaignId != null, AdStatsDailyEntity::getCampaignId, campaignId);
        Map<String, CreativeStatsAccumulator> accumulatorMap = new HashMap<>();
        adStatsDailyMapper.selectList(query).forEach(row -> {
            String key = row.getCampaignId() + ":" + row.getCreativeId();
            CreativeStatsAccumulator accumulator = accumulatorMap.computeIfAbsent(
                    key,
                    ignored -> new CreativeStatsAccumulator(row.getCampaignId(), row.getCreativeId(), row.getAdSlotId()));
            accumulator.add(row);
        });
        return accumulatorMap.values().stream()
                .map(CreativeStatsAccumulator::toVO)
                .sorted(Comparator.comparing(TopCreativeVO::costAmount).reversed()
                        .thenComparing(TopCreativeVO::clickCount, Comparator.reverseOrder())
                        .thenComparing(TopCreativeVO::impressionCount, Comparator.reverseOrder()))
                .limit(actualLimit)
                .toList();
    }

    private double divide(long numerator, long denominator) {
        if (denominator <= 0) {
            return 0D;
        }
        return (double) numerator / denominator;
    }

    private class CreativeStatsAccumulator {

        private final Long campaignId;
        private final Long creativeId;
        private final Long adSlotId;
        private long impressions;
        private long clicks;
        private long conversions;
        private long costAmount;

        private CreativeStatsAccumulator(Long campaignId, Long creativeId, Long adSlotId) {
            this.campaignId = campaignId;
            this.creativeId = creativeId;
            this.adSlotId = adSlotId;
        }

        private void add(AdStatsDailyEntity row) {
            impressions += row.getImpressionCount();
            clicks += row.getClickCount();
            conversions += row.getConversionCount();
            costAmount += row.getCostAmount();
        }

        private TopCreativeVO toVO() {
            return new TopCreativeVO(
                    campaignId,
                    creativeId,
                    adSlotId,
                    impressions,
                    clicks,
                    conversions,
                    costAmount,
                    divide(clicks, impressions),
                    divide(conversions, clicks));
        }
    }
}
