package com.example.adplatform.admin.controller;

import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.DeleteMapping;
import com.example.adplatform.admin.request.advertiserwallet.UpdateAdvertiserWalletRequest;
import com.example.adplatform.admin.request.advertiserwallet.DeleteAdvertiserWalletRequest;
import com.example.adplatform.admin.request.advertiserwallet.CreateAdvertiserWalletRequest;
import com.example.adplatform.admin.request.advertiserwallet.PageAdvertiserWalletRequest;
import com.example.adplatform.admin.response.advertiserwallet.AdvertiserWalletOperationResponse;
import com.example.adplatform.admin.service.AdvertiserWalletService;
import com.example.adplatform.admin.response.advertiserwallet.AdvertiserWalletQueryResponse;
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
@RequestMapping("/api/platform/advertiser-wallets")
public class AdvertiserWalletController {

    private final AdvertiserWalletService advertiserWalletService;

    @PostMapping("/create")
    public Result<AdvertiserWalletOperationResponse> create(@Valid @RequestBody CreateAdvertiserWalletRequest request) {
        return Result.success(advertiserWalletService.create(request));
    }

    @PostMapping("/page")
    public Result<PageResponse<AdvertiserWalletQueryResponse>> page(@Valid @RequestBody PageAdvertiserWalletRequest request) {
        return Result.success(advertiserWalletService.pageQuery(request));
    }

    @PutMapping("/update")
    public Result<AdvertiserWalletOperationResponse> update(@Valid @RequestBody UpdateAdvertiserWalletRequest request) {
        return Result.success(advertiserWalletService.update(request));
    }

    @DeleteMapping("/delete")
    public Result<AdvertiserWalletOperationResponse> delete(@Valid @RequestBody DeleteAdvertiserWalletRequest request) {
        return Result.success(advertiserWalletService.delete(request));
    }
}
