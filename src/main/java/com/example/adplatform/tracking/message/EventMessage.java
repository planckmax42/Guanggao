package com.example.adplatform.tracking.message;

import com.example.adplatform.tracking.dto.EventRequest;

import java.time.LocalDateTime;

/**
 * Kafka 中传递的广告事件消息，HTTP 上报成功后由消费者异步写入明细和统计。
 */
public record EventMessage(
        String eventId,
        String requestId,
        String eventType,
        Long materialId,
        Long viewerId,
        LocalDateTime eventTime) {

    public static EventMessage from(EventRequest request) {
        return new EventMessage(
                request.eventId(),
                request.requestId(),
                request.eventType(),
                request.materialId(),
                request.viewerId(),
                request.eventTime());
    }

    public EventRequest toRequest() {
        return new EventRequest(eventId, requestId, eventType, materialId, viewerId, eventTime);
    }
}
