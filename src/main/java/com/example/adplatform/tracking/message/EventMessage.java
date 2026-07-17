package com.example.adplatform.tracking.message;

import com.example.adplatform.tracking.entity.EventType;

import java.time.LocalDateTime;

/**
 * 异步传递的广告事件消息，HTTP 上报成功后由消息适配器转交给业务层处理。
 */
public record EventMessage(
        String eventId,
        String requestId,
        EventType eventType,
        Long materialId,
        Long viewerId,
        LocalDateTime eventTime) {
}
