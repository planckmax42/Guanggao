package com.example.adplatform.infra.kafka;

import com.example.adplatform.common.exception.BusinessException;
import com.example.adplatform.tracking.message.EventMessage;
import com.example.adplatform.tracking.service.EventProcessor;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.kafka.annotation.KafkaListener;
import org.springframework.stereotype.Component;

/**
 * Kafka 广告事件消费适配器。
 *
 * <p>该类只负责接收和记录消费结果，具体的事件入库、计费与统计逻辑交给
 * {@link EventProcessor} 处理。</p>
 */
@Slf4j
@RequiredArgsConstructor
@Component
public class EventKafkaConsumer {

    private final EventProcessor eventProcessor;

    /**
     * 异步消费广告事件，消费者线程负责写 MySQL 明细和 Redis 实时统计。
     *
     * @param message Kafka 反序列化得到的广告事件消息
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
