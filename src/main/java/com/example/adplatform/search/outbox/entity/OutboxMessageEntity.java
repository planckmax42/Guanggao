package com.example.adplatform.search.outbox.entity;

import lombok.Getter;
import lombok.Setter;

import java.time.LocalDateTime;

/**
 * outbox_message 表映射。
 *
 * <p>{@code eventId} 是 Outbox 业务幂等标识，{@code messageKey} 决定 Kafka 分区；
 * {@code nextRetryAt} 让失败消息在数据库中持久化退避时间。</p>
 */
@Getter
@Setter
public class OutboxMessageEntity {
    private Long id;
    private String eventId;
    private String topic;
    private String messageKey;
    private String messageType;
    private String payload;
    private String status;
    private Integer retryCount;
    private LocalDateTime nextRetryAt;
    private LocalDateTime createdAt;
    private LocalDateTime sentAt;
}
