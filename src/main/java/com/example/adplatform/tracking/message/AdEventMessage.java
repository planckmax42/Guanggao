package com.example.adplatform.tracking.message;

import com.example.adplatform.tracking.dto.AdEventRequest;

import java.time.LocalDateTime;

/**
 * Kafka 中传递的广告事件消息，HTTP 上报成功后由消费者异步写入明细和统计。
 */
public record AdEventMessage(
        String eventId,
        String requestId,
        String eventType,
        Long creativeId,
        Long userId,
        LocalDateTime eventTime) {

    public static AdEventMessage from(AdEventRequest request) {
        return new AdEventMessage(
                request.eventId(),
                request.requestId(),
                request.eventType(),
                request.creativeId(),
                request.userId(),
                request.eventTime());
    }

    public AdEventRequest toRequest() {
        return new AdEventRequest(eventId, requestId, eventType, creativeId, userId, eventTime);
    }
}
