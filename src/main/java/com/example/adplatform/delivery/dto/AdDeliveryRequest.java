package com.example.adplatform.delivery.dto;

import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

import java.util.List;

/**
 * 广告投放请求，包含广告位编码和用户上下文，供召回、定向过滤和排序使用。
 */
public record AdDeliveryRequest(
        @NotNull Long userId,
        @NotBlank @Size(max = 64) String slotCode,
        @Size(max = 64) String region,
        @Size(max = 32) String deviceType,
        @Min(0) @Max(120) Integer age,
        @Size(max = 32) String gender,
        @Size(max = 64) List<@Size(max = 64) String> tags,
        @Min(1) @Max(10) Integer size) {
}
