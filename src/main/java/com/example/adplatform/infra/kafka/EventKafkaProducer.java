package com.example.adplatform.infra.kafka;

import com.example.adplatform.common.exception.BusinessException;
import com.example.adplatform.common.exception.ErrorCode;
import com.example.adplatform.tracking.message.EventMessage;
import com.example.adplatform.tracking.port.EventPublisher;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.stereotype.Component;

import java.util.concurrent.TimeUnit;

/**
 * 基于 Kafka 实现的广告事件发布适配器。
 *
 * <p>业务层仅依赖 {@link EventPublisher}，由该类负责选择 Topic、设置消息 Key 并等待 Broker 确认。</p>
 */
@Component
public class EventKafkaProducer implements EventPublisher {

    private final KafkaTemplate<String, EventMessage> kafkaTemplate;

    public EventKafkaProducer(
            @Qualifier("eventKafkaTemplate") KafkaTemplate<String, EventMessage> kafkaTemplate) {
        this.kafkaTemplate = kafkaTemplate;
    }

    @Value("${app.kafka.topics.event}")
    private String eventTopic;

    /**
     * 等待 Kafka broker 确认写入，避免接口返回成功但消息实际没有进入 Kafka。
     *
     * @param message 待发布的广告事件消息
     * @throws BusinessException 发送失败、超时或线程被中断时抛出
     */
    @Override
    public void publish(EventMessage message) {
        try {
            kafkaTemplate.send(eventTopic, message.eventId(), message)
                    .get(3, TimeUnit.SECONDS);
        } catch (Exception ex) {
            throw new BusinessException(ErrorCode.SYSTEM_ERROR, "广告事件写入 Kafka 失败");
        }
    }
}
