package com.example.adplatform.search.outbox.entity;

import lombok.Getter;
import lombok.Setter;

import java.time.LocalDateTime;

/**
 * outbox_message 表映射。
 *
 * <p>{@code eventId} 是 Outbox 业务幂等标识，{@code messageKey} 决定 Kafka 分区。
 * 状态和重试字段仅供 polling 回退模式使用；Debezium 模式只消费 INSERT Binlog。</p>
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
