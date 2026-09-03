package com.example.adplatform.tracking.request;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;

import java.time.LocalDateTime;

import static com.example.adplatform.common.id.PublicIdGenerator.MATERIAL_PATTERN;

/**
 * 广告事件上报请求，客户端或业务系统通过它上报曝光、点击、转化事件。
 */
public record EventRequest(
        @NotBlank @Size(max = 128) String eventId,
        @Size(max = 128) String requestId,
        @NotBlank @Size(max = 32) String eventType,
        @NotBlank @Pattern(regexp = MATERIAL_PATTERN) String materialPublicId,
        @NotNull Long viewerId,
        LocalDateTime eventTime) {
}
