package com.example.adplatform.report.service;

import com.example.adplatform.report.vo.DailyReportVO;
import com.example.adplatform.report.vo.FunnelStatsVO;
import com.example.adplatform.report.vo.TopMaterialVO;

import java.time.LocalDate;
import java.util.List;

public interface ReportService {

    List<DailyReportVO> daily(LocalDate statDate, Long planId);

    FunnelStatsVO funnel(LocalDate startDate, LocalDate endDate, Long planId);

    List<TopMaterialVO> topMaterials(LocalDate startDate, LocalDate endDate, Long planId, Integer limit);
}
