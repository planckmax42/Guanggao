package com.example.adplatform.delivery.request;

import jakarta.validation.constraints.*;
import java.util.List;

/**
 * 广告投放请求，包含广告位编码和用户上下文，供召回、定向过滤和排序使用。
 */
public record AdDeliveryRequest(
        @NotNull Long userId,
        @NotBlank @Size(max = 64) String slotCode,
        @NotBlank @Size(max = 64) String region,
        @NotEmpty @Size(max = 16) List<@NotBlank @Size(max = 32) String> deviceTypes,
        @NotBlank @Size(max = 32) String gender,
        @NotNull @Min(0) @Max(120) Integer age,
        @NotEmpty @Size(max = 16) List<@NotBlank @Size(max = 32) String> tags,
        @NotNull @Min(1) @Max(10) Integer size) {
}
