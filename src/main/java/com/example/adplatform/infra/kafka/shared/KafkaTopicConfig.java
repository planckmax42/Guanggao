package com.example.adplatform.infra.kafka.shared;

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
    public NewTopic eventTopic(@Value("${app.kafka.topics.event}") String topicName) {//Tracking链路Topic
        return TopicBuilder.name(topicName)//指定Topic名称
                .partitions(3)//指定分区数
                .replicas(1)//指定副本数，本地单Broker环境，todo:后续考虑容灾多Broker部署
                .build();//根据链式配置构建对象
    }

    /** 配置变更 Outbox Topic；相同聚合 ID 通过消息 key 保持分区内有序。 */
    @Bean
    public NewTopic configChangeTopic(@Value("${app.kafka.topics.config-change}") String topicName) {//数据库变更到ES Topic
        return topic(topicName);
    }

    /** 配置同步超过重试次数后的死信 Topic。 */
    @Bean
    public NewTopic configChangeDltTopic(@Value("${app.kafka.topics.config-change-dlt}") String topicName) {//同步到ES失败使用的DLT，todo：给eventTopic增加DLT
        return topic(topicName);
    }

    private NewTopic topic(String topicName) {
        return TopicBuilder.name(topicName)
                .partitions(3)
                .replicas(1)
                .build();
    }
}
