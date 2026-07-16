package com.example.adplatform.search.outbox.entity;

import lombok.Getter;
import lombok.Setter;

import java.time.LocalDateTime;

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
