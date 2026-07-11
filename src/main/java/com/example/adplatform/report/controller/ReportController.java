package com.example.adplatform.report.controller;

import com.example.adplatform.common.response.Result;
import com.example.adplatform.report.service.ReportService;
import com.example.adplatform.report.vo.DailyReportVO;
import com.example.adplatform.report.vo.FunnelStatsVO;
import com.example.adplatform.report.vo.TopMaterialVO;
import lombok.RequiredArgsConstructor;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.time.LocalDate;
import java.util.List;

@RequiredArgsConstructor
@RestController
@RequestMapping("/api/report")
public class ReportController {

    private final ReportService reportService;

    /**
     * 查询某一天的广告统计明细，可按广告计划过滤。
     */
    @GetMapping("/daily")
    public Result<List<DailyReportVO>> daily(
            @RequestParam @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate statDate,
            @RequestParam(required = false) Long planId) {
        return Result.success(reportService.daily(statDate, planId));
    }

    /**
     * 查询指定日期范围内的曝光、点击、转化和消耗漏斗数据。
     */
    @GetMapping("/funnel")
    public Result<FunnelStatsVO> funnel(
            @RequestParam @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate startDate,
            @RequestParam @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate endDate,
            @RequestParam(required = false) Long planId) {
        return Result.success(reportService.funnel(startDate, endDate, planId));
    }

    /**
     * 查询指定日期范围内的广告素材效果排行，可按广告计划过滤。
     */
    @GetMapping("/top-materials")
    public Result<List<TopMaterialVO>> topMaterials(
            @RequestParam @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate startDate,
            @RequestParam @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate endDate,
            @RequestParam(required = false) Long planId,
            @RequestParam(required = false) Integer limit) {
        return Result.success(reportService.topMaterials(startDate, endDate, planId, limit));
    }
}
