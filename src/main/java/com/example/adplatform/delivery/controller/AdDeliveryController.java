package com.example.adplatform.delivery.controller;

import com.example.adplatform.common.response.Result;
import com.example.adplatform.delivery.dto.AdDeliveryRequest;
import com.example.adplatform.delivery.service.AdDeliveryService;
import com.example.adplatform.delivery.vo.AdDeliveryResponse;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RequiredArgsConstructor
@RestController
@RequestMapping("/api/delivery")
public class AdDeliveryController {

    private final AdDeliveryService adDeliveryService;

    /**
     * 根据广告位和用户上下文召回广告，并完成状态、预算、频控、定向过滤和排序。
     */
    @PostMapping("/ads")
    public Result<AdDeliveryResponse> deliver(@Valid @RequestBody AdDeliveryRequest request) {
        return Result.success(adDeliveryService.deliver(request));
    }
}
