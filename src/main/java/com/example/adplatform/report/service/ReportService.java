package com.example.adplatform.report.service;

import com.example.adplatform.report.response.DailyReportResponse;
import com.example.adplatform.report.response.FunnelStatsResponse;
import com.example.adplatform.report.response.TopMaterialResponse;

import java.time.LocalDate;
import java.util.List;

public interface ReportService {

    List<DailyReportResponse> daily(LocalDate statDate, String planPublicId);

    FunnelStatsResponse funnel(LocalDate startDate, LocalDate endDate, String planPublicId);

    List<TopMaterialResponse> topMaterials(
            LocalDate startDate,
            LocalDate endDate,
            String planPublicId,
            Integer limit);
}
