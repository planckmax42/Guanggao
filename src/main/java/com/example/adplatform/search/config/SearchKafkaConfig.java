package com.example.adplatform.search.config;

import org.apache.kafka.clients.consumer.ConsumerConfig;
import org.apache.kafka.clients.producer.ProducerConfig;
import org.apache.kafka.common.TopicPartition;
import org.apache.kafka.common.serialization.StringDeserializer;
import org.apache.kafka.common.serialization.StringSerializer;
import org.springframework.boot.autoconfigure.kafka.KafkaProperties;
import org.springframework.boot.ssl.SslBundles;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.kafka.config.ConcurrentKafkaListenerContainerFactory;
import org.springframework.kafka.core.DefaultKafkaConsumerFactory;
import org.springframework.kafka.core.DefaultKafkaProducerFactory;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.kafka.listener.DeadLetterPublishingRecoverer;
import org.springframework.kafka.listener.DefaultErrorHandler;
import org.springframework.kafka.support.serializer.JsonSerializer;
import org.springframework.util.backoff.FixedBackOff;
import com.example.adplatform.tracking.message.EventMessage;

import java.util.HashMap;
import java.util.Map;

/**
 * 搜索链路专用 Kafka 序列化和失败恢复配置。
 *
 * <p>原始广告事件使用 JSON 对象模板；Outbox 中已经持久化的是 JSON 字符串，因此使用
 * 独立的 String 模板和监听容器，避免全局 JsonDeserializer 把消息反序列化成错误类型。
 * 候选同步消费者失败后固定间隔重试 4 次，最终转发到与原 Topic 同分区号的 DLT。</p>
 */
@Configuration
public class SearchKafkaConfig {

    /** 发送原始曝光、点击、转化事件的强类型模板。 */
    @Bean("eventKafkaTemplate")
    public KafkaTemplate<String, EventMessage> eventKafkaTemplate(
            KafkaProperties properties,
            SslBundles sslBundles) {
        Map<String, Object> config = new HashMap<>(properties.buildProducerProperties(sslBundles));
        config.put(ProducerConfig.KEY_SERIALIZER_CLASS_CONFIG, StringSerializer.class);
        config.put(ProducerConfig.VALUE_SERIALIZER_CLASS_CONFIG, JsonSerializer.class);
        return new KafkaTemplate<>(new DefaultKafkaProducerFactory<>(config));
    }

    /** 发布 Outbox JSON payload 的字符串模板。 */
    @Bean("outboxKafkaTemplate")
    public KafkaTemplate<String, String> outboxKafkaTemplate(
            KafkaProperties properties,
            SslBundles sslBundles) {
        Map<String, Object> config = new HashMap<>(properties.buildProducerProperties(sslBundles));
        config.put(ProducerConfig.KEY_SERIALIZER_CLASS_CONFIG, StringSerializer.class);
        config.put(ProducerConfig.VALUE_SERIALIZER_CLASS_CONFIG, StringSerializer.class);
        return new KafkaTemplate<>(new DefaultKafkaProducerFactory<>(config));
    }

    /** 创建配置同步消费者使用的字符串监听容器。 */
    @Bean("searchKafkaListenerContainerFactory")
    public ConcurrentKafkaListenerContainerFactory<String, String> searchKafkaListenerContainerFactory(
            KafkaProperties properties,
            SslBundles sslBundles,
            KafkaTemplate<String, String> outboxKafkaTemplate) {
        Map<String, Object> config = new HashMap<>(properties.buildConsumerProperties(sslBundles));
        config.put(ConsumerConfig.KEY_DESERIALIZER_CLASS_CONFIG, StringDeserializer.class);
        config.put(ConsumerConfig.VALUE_DESERIALIZER_CLASS_CONFIG, StringDeserializer.class);
        // application-local.yml 为原始事件消费者配置了默认 JSON 类型；字符串消费者必须移除。
        config.remove("spring.json.value.default.type");
        config.remove("spring.json.trusted.packages");
        config.remove("spring.json.use.type.headers");

        ConcurrentKafkaListenerContainerFactory<String, String> factory =
                new ConcurrentKafkaListenerContainerFactory<>();
        factory.setConsumerFactory(new DefaultKafkaConsumerFactory<>(config));
        factory.setAutoStartup(properties.getListener().isAutoStartup());
        DeadLetterPublishingRecoverer recoverer = new DeadLetterPublishingRecoverer(
                outboxKafkaTemplate,
                // 保持原分区可以保留同一业务 key 的诊断顺序。
                (record, ex) -> new TopicPartition(record.topic() + "-dlt", record.partition()));
        factory.setCommonErrorHandler(new DefaultErrorHandler(recoverer, new FixedBackOff(1000L, 4L)));
        return factory;
    }
}
