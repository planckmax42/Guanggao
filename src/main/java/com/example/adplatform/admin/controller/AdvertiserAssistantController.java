package com.example.adplatform.admin.controller;

import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.DeleteMapping;
import com.example.adplatform.admin.request.advertiserassistant.UpdateAdvertiserAssistantRequest;
import com.example.adplatform.admin.request.advertiserassistant.DeleteAdvertiserAssistantRequest;
import com.example.adplatform.admin.request.advertiserassistant.CreateAdvertiserAssistantRequest;
import com.example.adplatform.admin.request.advertiserassistant.PageAdvertiserAssistantRequest;
import com.example.adplatform.admin.response.advertiserassistant.AdvertiserAssistantOperationResponse;
import com.example.adplatform.admin.service.AdvertiserAssistantService;
import com.example.adplatform.admin.response.advertiserassistant.AdvertiserAssistantQueryResponse;
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
@RequestMapping("/api/advertiser/assistants")
public class AdvertiserAssistantController {

    private final AdvertiserAssistantService advertiserAssistantService;

    @PostMapping("/create")
    public Result<AdvertiserAssistantOperationResponse> create(@Valid @RequestBody CreateAdvertiserAssistantRequest request) {
        return Result.success(advertiserAssistantService.create(request));
    }

    @PostMapping("/page")
    public Result<PageResponse<AdvertiserAssistantQueryResponse>> page(@Valid @RequestBody PageAdvertiserAssistantRequest request) {
        return Result.success(advertiserAssistantService.pageQuery(request));
    }

    @PutMapping("/update")
    public Result<AdvertiserAssistantOperationResponse> update(@Valid @RequestBody UpdateAdvertiserAssistantRequest request) {
        return Result.success(advertiserAssistantService.update(request));
    }

    @DeleteMapping("/delete")
    public Result<AdvertiserAssistantOperationResponse> delete(@Valid @RequestBody DeleteAdvertiserAssistantRequest request) {
        return Result.success(advertiserAssistantService.delete(request));
    }
}
