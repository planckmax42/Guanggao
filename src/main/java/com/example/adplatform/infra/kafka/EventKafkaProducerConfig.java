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

    @Bean("eventKafkaTemplate")//todo:为什么不能用@Component
    public KafkaTemplate<String, EventMessage> eventKafkaTemplate(
            KafkaProperties kafkaProperties,//官方配置，对应yml的spring.kafka.....
            SslBundles sslBundles,
            EventKafkaProducerProperties eventProperties) {//自定义配置的值，对应yml的app.kafaka.....
        Map<String, Object> config = new HashMap<>(kafkaProperties.buildProducerProperties(sslBundles));//先导入官方配置，后续put自定义配置
        config.put(ProducerConfig.KEY_SERIALIZER_CLASS_CONFIG, StringSerializer.class);//消息key序列化器配置,类型为string
        config.put(ProducerConfig.VALUE_SERIALIZER_CLASS_CONFIG, JsonSerializer.class);//消息value序列化器配置，类型为JSON
        config.put(ProducerConfig.ACKS_CONFIG, "all");//所有broker确认后才算成功，单机情况下只有一个broker
        config.put(ProducerConfig.ENABLE_IDEMPOTENCE_CONFIG, true);//开启幂等发送，todo结合后面eventId看看幂等性的区别
        config.put(ProducerConfig.DELIVERY_TIMEOUT_MS_CONFIG, Math.toIntExact(eventProperties.getDeliveryTimeout().toMillis()));//整条消息所有尝试的总时间
        config.put(ProducerConfig.REQUEST_TIMEOUT_MS_CONFIG, Math.toIntExact(eventProperties.getRequestTimeout().toMillis()));//单次尝试的等待时间
        config.put(ProducerConfig.RETRY_BACKOFF_MS_CONFIG, eventProperties.getRetryBackoff().toMillis());//两次等待的时间间隔
        config.put(ProducerConfig.MAX_BLOCK_MS_CONFIG, eventProperties.getMaxBlock().toMillis());//最大的阻塞时间，等待Topic元数据/本地缓冲区满了
        config.put(ProducerConfig.LINGER_MS_CONFIG, eventProperties.getLinger().toMillis());//为了凑批次，最多等待多久
        config.put(ProducerConfig.BATCH_SIZE_CONFIG, eventProperties.getBatchSize());//同一个分区的消息批次大小（单位是字节）
        config.put(ProducerConfig.COMPRESSION_TYPE_CONFIG, eventProperties.getCompressionType());//消息批次压缩算法
        return new KafkaTemplate<>(new DefaultKafkaProducerFactory<>(config));
    }
}
