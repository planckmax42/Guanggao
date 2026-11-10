package com.example.adplatform.report.controller;

import com.example.adplatform.common.response.Result;
import com.example.adplatform.report.request.DailyReportRequest;
import com.example.adplatform.report.request.FunnelReportRequest;
import com.example.adplatform.report.request.TopMaterialsRequest;
import com.example.adplatform.report.service.ReportService;
import com.example.adplatform.report.response.DailyReportResponse;
import com.example.adplatform.report.response.FunnelStatsResponse;
import com.example.adplatform.report.response.TopMaterialResponse;
import lombok.RequiredArgsConstructor;
import jakarta.validation.Valid;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

@RequiredArgsConstructor
@Validated
@RestController
@RequestMapping("/api/report")
public class ReportController {

    private final ReportService reportService;

    /**
     * 查询某一天的广告统计明细，可按广告计划过滤。
     */
    @PostMapping("/daily")
    public Result<List<DailyReportResponse>> daily(@Valid @RequestBody DailyReportRequest request) {
        return Result.success(reportService.daily(request.statDate(), request.planPublicId()));
    }

    /**
     * 查询指定日期范围内的曝光、点击、转化和消耗漏斗数据。
     */
    @PostMapping("/funnel")
    public Result<FunnelStatsResponse> funnel(@Valid @RequestBody FunnelReportRequest request) {
        return Result.success(reportService.funnel(request.startDate(), request.endDate(), request.planPublicId()));
    }

    /**
     * 查询指定日期范围内的广告素材效果排行，可按广告计划过滤。
     */
    @PostMapping("/top-materials")
    public Result<List<TopMaterialResponse>> topMaterials(@Valid @RequestBody TopMaterialsRequest request) {
        return Result.success(reportService.topMaterials(
                request.startDate(), request.endDate(), request.planPublicId(), request.limit()));
    }
}
