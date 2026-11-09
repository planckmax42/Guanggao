package com.example.adplatform.admin.controller;

import com.example.adplatform.admin.request.advertiser.CreateAdvertiserRequest;
import com.example.adplatform.admin.request.advertiser.PageAdvertiserRequest;
import com.example.adplatform.admin.response.advertiser.AdvertiserOperationResponse;
import com.example.adplatform.admin.service.AdvertiserService;
import com.example.adplatform.admin.response.advertiser.AdvertiserQueryResponse;
import com.example.adplatform.admin.response.PageResponse;
import com.example.adplatform.common.response.Result;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@Validated
@RequiredArgsConstructor
@RestController
@RequestMapping("/api/platform/advertisers")
public class AdvertiserController {

    private final AdvertiserService advertiserService;

    /**
     * 创建广告主账号，作为广告计划的归属主体。
     */
    @PostMapping
    public Result<AdvertiserOperationResponse> create(@Valid @RequestBody CreateAdvertiserRequest request) {
        return Result.success(advertiserService.create(request));
    }

    /**
     * 分页查询广告主，支持按名称关键字和启用状态筛选。
     */
    @PostMapping("/page")
    public Result<PageResponse<AdvertiserQueryResponse>> page(@Valid @RequestBody PageAdvertiserRequest request) {
        return Result.success(advertiserService.pageQuery(
                request.current(), request.size(), request.status()));
    }
}
