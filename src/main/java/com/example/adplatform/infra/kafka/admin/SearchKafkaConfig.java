package com.example.adplatform.infra.kafka.admin;

import org.apache.kafka.clients.consumer.ConsumerConfig;
import org.apache.kafka.clients.producer.ProducerConfig;
import org.apache.kafka.common.TopicPartition;
import org.apache.kafka.common.serialization.StringDeserializer;
import org.apache.kafka.common.serialization.StringSerializer;
import org.springframework.boot.autoconfigure.kafka.KafkaProperties;
import org.springframework.boot.ssl.SslBundles;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.kafka.config.ConcurrentKafkaListenerContainerFactory;
import org.springframework.kafka.config.KafkaListenerEndpointRegistry;
import org.springframework.kafka.core.DefaultKafkaConsumerFactory;
import org.springframework.kafka.core.DefaultKafkaProducerFactory;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.kafka.listener.DeadLetterPublishingRecoverer;
import org.springframework.kafka.listener.DefaultErrorHandler;
import org.springframework.kafka.listener.ContainerPausingBackOffHandler;
import org.springframework.kafka.listener.ListenerContainerPauseService;
import org.springframework.kafka.listener.RetryListener;
import org.springframework.kafka.KafkaException;
import org.springframework.dao.DataAccessException;
import com.example.adplatform.infra.redis.delivery.slot.SlotCacheAccessException;
import com.example.adplatform.infra.redis.delivery.slot.SlotCacheFailureLogLimiter;
import io.github.resilience4j.circuitbreaker.CallNotPermittedException;
import io.micrometer.core.instrument.MeterRegistry;
import org.springframework.util.backoff.ExponentialBackOff;
import org.springframework.util.backoff.FixedBackOff;
import org.springframework.scheduling.TaskScheduler;
import lombok.extern.slf4j.Slf4j;

import java.util.HashMap;
import java.util.Map;

/**
 * 搜索链路专用 Kafka 序列化和失败恢复配置。
 *
 * <p>Outbox 中已经持久化的是 JSON 字符串，因此使用独立的 String 模板和监听容器，
 * 避免全局 JsonDeserializer 把消息反序列化成错误类型。
 * 候选同步消费者失败后有限重试并进入 DLT；广告位缓存的瞬时故障持续退避至恢复。</p>
 */
@Configuration
@Slf4j
public class SearchKafkaConfig {

    /** 发布 Outbox JSON payload 的字符串模板。 */
    @Bean("outboxKafkaTemplate")
    public KafkaTemplate<String, String> outboxKafkaTemplate(
            KafkaProperties properties,
            SslBundles sslBundles) {
        Map<String, Object> config = new HashMap<>(properties.buildProducerProperties(sslBundles));
        config.put(ProducerConfig.KEY_SERIALIZER_CLASS_CONFIG, StringSerializer.class);
        config.put(ProducerConfig.VALUE_SERIALIZER_CLASS_CONFIG, StringSerializer.class);
        config.put(ProducerConfig.ACKS_CONFIG, "all");
        config.put(ProducerConfig.ENABLE_IDEMPOTENCE_CONFIG, true);
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

    /** Redis 瞬时故障持续退避，非重试异常立即进入独立 DLT。 */
    @Bean("slotCacheKafkaListenerContainerFactory")
    public ConcurrentKafkaListenerContainerFactory<String, String> slotCacheKafkaListenerContainerFactory(
            KafkaProperties properties,
            SslBundles sslBundles,
            @Qualifier("outboxKafkaTemplate") KafkaTemplate<String, String> outboxKafkaTemplate,
            @Value("${app.kafka.topics.slot-cache-sync-dlt}") String dltTopic,
            MeterRegistry meterRegistry,
            SlotCacheFailureLogLimiter failureLogLimiter,
            KafkaListenerEndpointRegistry registry,
            TaskScheduler taskScheduler) {
        Map<String, Object> config = new HashMap<>(properties.buildConsumerProperties(sslBundles));
        config.put(ConsumerConfig.KEY_DESERIALIZER_CLASS_CONFIG, StringDeserializer.class);
        config.put(ConsumerConfig.VALUE_DESERIALIZER_CLASS_CONFIG, StringDeserializer.class);
        config.remove("spring.json.value.default.type");
        config.remove("spring.json.trusted.packages");
        config.remove("spring.json.use.type.headers");

        DeadLetterPublishingRecoverer recoverer = new DeadLetterPublishingRecoverer(
                outboxKafkaTemplate,
                (record, ex) -> new TopicPartition(dltTopic, record.partition()));
        ExponentialBackOff backOff = new ExponentialBackOff(1_000L, 2D);
        backOff.setMaxInterval(30_000L);
        backOff.setMaxElapsedTime(Long.MAX_VALUE);
        ListenerContainerPauseService pauseService =
                new ListenerContainerPauseService(registry, taskScheduler);
        DefaultErrorHandler errorHandler = new DefaultErrorHandler(
                recoverer,
                backOff,
                new ContainerPausingBackOffHandler(pauseService));
        errorHandler.defaultFalse();
        errorHandler.addRetryableExceptions(
                SlotCacheAccessException.class,
                DataAccessException.class,
                CallNotPermittedException.class);
        errorHandler.setRetryListeners(new RetryListener() {
            @Override
            public void failedDelivery(
                    org.apache.kafka.clients.consumer.ConsumerRecord<?, ?> record,
                    Exception ex,
                    int deliveryAttempt) {
                meterRegistry.counter("ad.slot.cache.sync", "stage", "consumer", "result", "retry")
                        .increment();
                if (failureLogLimiter.shouldLog("consumer_retry")) {
                    log.warn("广告位缓存同步失败，将继续退避重试，topic={}，partition={}，offset={}，key={}，attempt={}",
                            record.topic(), record.partition(), record.offset(), record.key(), deliveryAttempt, ex);
                }
            }

            @Override
            public void recovered(org.apache.kafka.clients.consumer.ConsumerRecord<?, ?> record, Exception ex) {
                meterRegistry.counter("ad.slot.cache.sync", "stage", "consumer", "result", "dlt")
                        .increment();
                log.error("广告位缓存同步消息已进入 DLT，topic={}，partition={}，offset={}，key={}",
                        record.topic(), record.partition(), record.offset(), record.key(), ex);
            }
        });
        // 瞬时故障以指标观测，避免每次退避都输出 WARN 堆栈。
        errorHandler.setLogLevel(KafkaException.Level.DEBUG);

        ConcurrentKafkaListenerContainerFactory<String, String> factory =
                new ConcurrentKafkaListenerContainerFactory<>();
        factory.setConsumerFactory(new DefaultKafkaConsumerFactory<>(config));
        factory.setCommonErrorHandler(errorHandler);
        factory.setAutoStartup(properties.getListener().isAutoStartup());
        return factory;
    }
}
