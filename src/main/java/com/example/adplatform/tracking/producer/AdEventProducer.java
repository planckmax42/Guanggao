package com.example.adplatform.tracking.producer;

import com.example.adplatform.common.exception.BusinessException;
import com.example.adplatform.common.exception.ErrorCode;
import com.example.adplatform.tracking.dto.AdEventRequest;
import com.example.adplatform.tracking.message.AdEventMessage;
import lombok.RequiredArgsConstructor;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.stereotype.Component;

import java.util.concurrent.TimeUnit;

@RequiredArgsConstructor
@Component
public class AdEventProducer {

    private final KafkaTemplate<String, AdEventMessage> kafkaTemplate;

    @Value("${app.kafka.topics.ad-event}")
    private String adEventTopic;

    /**
     * 等待 Kafka broker 确认写入，避免接口返回成功但消息实际没有进入 Kafka。
     */
    public void send(AdEventRequest request) {
        try {
            kafkaTemplate.send(adEventTopic, request.eventId(), AdEventMessage.from(request))
                    .get(3, TimeUnit.SECONDS);
        } catch (Exception ex) {
            throw new BusinessException(ErrorCode.SYSTEM_ERROR, "广告事件写入 Kafka 失败");
        }
    }
}
