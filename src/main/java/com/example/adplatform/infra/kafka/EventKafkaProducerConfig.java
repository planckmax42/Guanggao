package com.example.adplatform.infra.kafka;

import com.example.adplatform.tracking.message.EventMessage;
import org.apache.kafka.clients.producer.ProducerConfig;
import org.apache.kafka.common.serialization.StringSerializer;
import org.springframework.boot.autoconfigure.kafka.KafkaProperties;
import org.springframework.boot.ssl.SslBundles;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.kafka.core.DefaultKafkaProducerFactory;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.kafka.support.serializer.JsonSerializer;

import java.util.HashMap;
import java.util.Map;

/**
 * 原始曝光、点击和转化事件专用的 Kafka Producer 配置。
 */
@Configuration
public class EventKafkaProducerConfig {

    @Bean("eventKafkaTemplate")
    public KafkaTemplate<String, EventMessage> eventKafkaTemplate(
            KafkaProperties kafkaProperties,
            SslBundles sslBundles,
            EventKafkaProducerProperties eventProperties) {
        Map<String, Object> config =
                new HashMap<>(kafkaProperties.buildProducerProperties(sslBundles));
        config.put(ProducerConfig.KEY_SERIALIZER_CLASS_CONFIG, StringSerializer.class);
        config.put(ProducerConfig.VALUE_SERIALIZER_CLASS_CONFIG, JsonSerializer.class);
        config.put(ProducerConfig.ACKS_CONFIG, "all");
        config.put(ProducerConfig.ENABLE_IDEMPOTENCE_CONFIG, true);
        config.put(ProducerConfig.DELIVERY_TIMEOUT_MS_CONFIG,
                Math.toIntExact(eventProperties.getDeliveryTimeout().toMillis()));
        config.put(ProducerConfig.REQUEST_TIMEOUT_MS_CONFIG,
                Math.toIntExact(eventProperties.getRequestTimeout().toMillis()));
        config.put(ProducerConfig.RETRY_BACKOFF_MS_CONFIG,
                eventProperties.getRetryBackoff().toMillis());
        config.put(ProducerConfig.MAX_BLOCK_MS_CONFIG,
                eventProperties.getMaxBlock().toMillis());
        config.put(ProducerConfig.LINGER_MS_CONFIG,
                eventProperties.getLinger().toMillis());
        config.put(ProducerConfig.BATCH_SIZE_CONFIG, eventProperties.getBatchSize());
        config.put(ProducerConfig.COMPRESSION_TYPE_CONFIG, eventProperties.getCompressionType());
        return new KafkaTemplate<>(new DefaultKafkaProducerFactory<>(config));
    }
}
