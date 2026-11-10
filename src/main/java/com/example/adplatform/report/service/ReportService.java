package com.example.adplatform.report.service;

import com.example.adplatform.report.request.TopMaterialsRequest;
import com.example.adplatform.report.request.FunnelReportRequest;
import com.example.adplatform.report.request.DailyReportRequest;
import com.example.adplatform.report.response.DailyReportResponse;
import com.example.adplatform.report.response.FunnelStatsResponse;
import com.example.adplatform.report.response.TopMaterialResponse;

import java.util.List;

public interface ReportService {

    List<DailyReportResponse> daily(DailyReportRequest request);

    FunnelStatsResponse funnel(FunnelReportRequest request);

    List<TopMaterialResponse> topMaterials(TopMaterialsRequest request);
}
