package com.example.adplatform.report.service;

import com.example.adplatform.report.vo.DailyStatsVO;
import com.example.adplatform.report.vo.FunnelStatsVO;
import com.example.adplatform.report.vo.TopCreativeVO;

import java.time.LocalDate;
import java.util.List;

public interface ReportService {

    List<DailyStatsVO> daily(LocalDate statDate, Long campaignId);

    FunnelStatsVO funnel(LocalDate startDate, LocalDate endDate, Long campaignId);

    List<TopCreativeVO> topCreatives(LocalDate startDate, LocalDate endDate, Long campaignId, Integer limit);
}
