package com.example.adplatform.admin.controller;

import com.example.adplatform.admin.dto.CreateAdvertiserRequest;
import com.example.adplatform.admin.service.AdvertiserService;
import com.example.adplatform.admin.vo.AdvertiserVO;
import com.example.adplatform.common.response.PageResponse;
import com.example.adplatform.common.response.ResourceRefVO;
import com.example.adplatform.common.response.Result;
import jakarta.validation.Valid;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import lombok.RequiredArgsConstructor;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@Validated
@RequiredArgsConstructor
@RestController
@RequestMapping("/api/admin/advertisers")
public class AdvertiserController {

    private final AdvertiserService advertiserService;

    /**
     * 创建广告主账号，作为广告计划的归属主体。
     */
    @PostMapping
    public Result<ResourceRefVO> create(@Valid @RequestBody CreateAdvertiserRequest request) {
        return Result.success(advertiserService.create(request));
    }

    /**
     * 分页查询广告主，支持按名称关键字和启用状态筛选。
     */
    @GetMapping("/page")
    public Result<PageResponse<AdvertiserVO>> page(
            @RequestParam(defaultValue = "1") @Min(1) long current,
            @RequestParam(defaultValue = "10") @Min(1) @Max(100) long size,
            @RequestParam(required = false) String name,
            @RequestParam(required = false) Integer status) {
        return Result.success(advertiserService.pageQuery(current, size, name, status));
    }
}
