package com.example.adplatform.report.controller;

import com.example.adplatform.common.response.Result;
import com.example.adplatform.report.service.ReportService;
import com.example.adplatform.report.response.DailyReportResponse;
import com.example.adplatform.report.response.FunnelStatsResponse;
import com.example.adplatform.report.response.TopMaterialResponse;
import lombok.RequiredArgsConstructor;
import jakarta.validation.constraints.Pattern;
import org.springframework.validation.annotation.Validated;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.time.LocalDate;
import java.util.List;

import static com.example.adplatform.common.id.PublicIdGenerator.PLAN_PATTERN;

@RequiredArgsConstructor
@Validated
@RestController
@RequestMapping("/api/report")
public class ReportController {

    private final ReportService reportService;

    /**
     * 查询某一天的广告统计明细，可按广告计划过滤。
     */
    @GetMapping("/daily")
    public Result<List<DailyReportResponse>> daily(
            @RequestParam @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate statDate,
            @RequestParam(required = false) @Pattern(regexp = PLAN_PATTERN) String planPublicId) {
        return Result.success(reportService.daily(statDate, planPublicId));
    }

    /**
     * 查询指定日期范围内的曝光、点击、转化和消耗漏斗数据。
     */
    @GetMapping("/funnel")
    public Result<FunnelStatsResponse> funnel(
            @RequestParam @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate startDate,
            @RequestParam @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate endDate,
            @RequestParam(required = false) @Pattern(regexp = PLAN_PATTERN) String planPublicId) {
        return Result.success(reportService.funnel(startDate, endDate, planPublicId));
    }

    /**
     * 查询指定日期范围内的广告素材效果排行，可按广告计划过滤。
     */
    @GetMapping("/top-materials")
    public Result<List<TopMaterialResponse>> topMaterials(
            @RequestParam @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate startDate,
            @RequestParam @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate endDate,
            @RequestParam(required = false) @Pattern(regexp = PLAN_PATTERN) String planPublicId,
            @RequestParam(required = false) Integer limit) {
        return Result.success(reportService.topMaterials(startDate, endDate, planPublicId, limit));
    }
}
