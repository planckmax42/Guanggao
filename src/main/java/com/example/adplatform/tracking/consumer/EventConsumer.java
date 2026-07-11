package com.example.adplatform.tracking.consumer;

import com.example.adplatform.common.exception.BusinessException;
import com.example.adplatform.tracking.message.EventMessage;
import com.example.adplatform.tracking.service.EventProcessor;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.kafka.annotation.KafkaListener;
import org.springframework.stereotype.Component;

@Slf4j
@RequiredArgsConstructor
@Component
public class EventConsumer {

    private final EventProcessor eventProcessor;

    /**
     * 异步消费广告事件，消费者线程负责写 MySQL 明细和 Redis 实时统计。
     */
    @KafkaListener(topics = "${app.kafka.topics.event}")
    public void consume(EventMessage message) {
        try {
            eventProcessor.process(message);
        } catch (BusinessException ex) {
            log.warn("广告事件消息业务校验失败，eventId={}，原因={}", message.eventId(), ex.getMessage());
        }
    }
}
