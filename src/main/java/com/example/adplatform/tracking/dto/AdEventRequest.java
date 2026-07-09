package com.example.adplatform.tracking.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

import java.time.LocalDateTime;

public record AdEventRequest(
        @NotBlank @Size(max = 128) String eventId,
        @Size(max = 128) String requestId,
        @NotBlank @Size(max = 32) String eventType,
        @NotNull Long campaignId,
        @NotNull Long creativeId,
        Long adSlotId,
        @NotNull Long userId,
        LocalDateTime eventTime) {
}
