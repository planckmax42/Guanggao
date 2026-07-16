package com.example.adplatform.infra.kafka;

import org.apache.kafka.clients.admin.NewTopic;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.kafka.config.TopicBuilder;

/** 广告事件 Kafka Topic 的声明式创建配置。 */
@Configuration
public class KafkaTopicConfig {

    /**
     * 创建广告事件 Topic 定义，并在 Kafka Admin 可用时自动创建或校验 Topic。
     *
     * @param topicName {@code app.kafka.topics.event} 配置的 Topic 名称
     * @return 包含分区数和副本数的 Topic 定义
     */
    @Bean
    public NewTopic eventTopic(@Value("${app.kafka.topics.event}") String topicName) {
        return TopicBuilder.name(topicName)
                .partitions(3)
                .replicas(1)
                .build();
    }

    /** 配置变更 Outbox Topic；相同聚合 ID 通过消息 key 保持分区内有序。 */
    @Bean
    public NewTopic configChangeTopic(@Value("${app.kafka.topics.config-change}") String topicName) {
        return topic(topicName);
    }

    /** 已落 MySQL 的事件进入 ES 前使用的二级索引 Topic。 */
    @Bean
    public NewTopic eventIndexTopic(@Value("${app.kafka.topics.event-index}") String topicName) {
        return topic(topicName);
    }

    /** 配置同步超过重试次数后的死信 Topic。 */
    @Bean
    public NewTopic configChangeDltTopic(@Value("${app.kafka.topics.config-change-dlt}") String topicName) {
        return topic(topicName);
    }

    /** 事件索引超过重试次数后的死信 Topic。 */
    @Bean
    public NewTopic eventIndexDltTopic(@Value("${app.kafka.topics.event-index-dlt}") String topicName) {
        return topic(topicName);
    }

    private NewTopic topic(String topicName) {
        return TopicBuilder.name(topicName).partitions(3).replicas(1).build();
    }
}
