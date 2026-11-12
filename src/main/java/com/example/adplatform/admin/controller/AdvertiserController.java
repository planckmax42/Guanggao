package com.example.adplatform.admin.controller;

import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.DeleteMapping;
import com.example.adplatform.admin.request.advertiser.UpdateAdvertiserRequest;
import com.example.adplatform.admin.request.advertiser.DeleteAdvertiserRequest;
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

    @PostMapping("/create")
    public Result<AdvertiserOperationResponse> create(@Valid @RequestBody CreateAdvertiserRequest request) {
        return Result.success(advertiserService.create(request));
    }

    @PostMapping("/page")
    public Result<PageResponse<AdvertiserQueryResponse>> page(@Valid @RequestBody PageAdvertiserRequest request) {
        return Result.success(advertiserService.pageQuery(request));
    }

    @PutMapping("/update")
    public Result<AdvertiserOperationResponse> update(@Valid @RequestBody UpdateAdvertiserRequest request) {
        return Result.success(advertiserService.update(request));
    }

    @DeleteMapping("/delete")
    public Result<AdvertiserOperationResponse> delete(@Valid @RequestBody DeleteAdvertiserRequest request) {
        return Result.success(advertiserService.delete(request));
    }
}
