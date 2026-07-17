package com.example.adplatform.infra.kafka;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.apache.kafka.clients.admin.AdminClient;
import org.apache.kafka.clients.consumer.OffsetAndMetadata;
import org.apache.kafka.common.TopicPartition;
import org.apache.kafka.common.errors.GroupIdNotFoundException;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.kafka.config.KafkaListenerEndpointRegistry;
import org.springframework.kafka.core.KafkaAdmin;
import org.springframework.kafka.listener.MessageListenerContainer;
import org.springframework.stereotype.Component;

import java.util.List;
import java.util.Map;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.TimeUnit;

/**
 * 首次拆分 Consumer Group 时复制旧 tracking-consumer 的 offset，避免历史消息重复统计。
 * 新 Group 一旦已经提交过 offset，后续启动绝不会覆盖它。
 */
@Slf4j
@RequiredArgsConstructor
@Component
public class EventConsumerGroupOffsetInitializer implements ApplicationRunner {

    private static final List<String> LISTENER_IDS = List.of(
            "event-archive-listener",
            "event-billing-listener",
            "event-statistics-listener");

    private final KafkaAdmin kafkaAdmin;
    private final KafkaListenerEndpointRegistry listenerRegistry;

    @Value("${app.kafka.consumer-groups.legacy:tracking-consumer}")
    private String legacyGroup;

    @Value("${app.kafka.consumer-groups.archive}")
    private String archiveGroup;

    @Value("${app.kafka.consumer-groups.billing}")
    private String billingGroup;

    @Value("${app.kafka.consumer-groups.statistics}")
    private String statisticsGroup;

    @Override
    public void run(ApplicationArguments args) {
        try (AdminClient adminClient = AdminClient.create(kafkaAdmin.getConfigurationProperties())) {
            Map<TopicPartition, OffsetAndMetadata> legacyOffsets = offsets(adminClient, legacyGroup);
            for (String targetGroup : List.of(archiveGroup, billingGroup, statisticsGroup)) {
                initializeIfEmpty(adminClient, targetGroup, legacyOffsets);
            }
        } catch (Exception ex) {
            throw new IllegalStateException("初始化事件 Consumer Group offset 失败，已阻止 Listener 启动", ex);
        }
        startEventListeners();
    }

    private void initializeIfEmpty(
            AdminClient adminClient,
            String targetGroup,
            Map<TopicPartition, OffsetAndMetadata> legacyOffsets) throws Exception {
        if (!offsets(adminClient, targetGroup).isEmpty()) {
            log.info("事件 Consumer Group 已有 offset，跳过迁移：group={}", targetGroup);
            return;
        }
        if (legacyOffsets.isEmpty()) {
            log.info("旧 Consumer Group 没有 offset，{} 将按 auto-offset-reset 启动", targetGroup);
            return;
        }
        adminClient.alterConsumerGroupOffsets(targetGroup, legacyOffsets)
                .all()
                .get(10, TimeUnit.SECONDS);
        log.info("已从 {} 复制 {} 个分区 offset 到 {}",
                legacyGroup, legacyOffsets.size(), targetGroup);
    }

    private Map<TopicPartition, OffsetAndMetadata> offsets(
            AdminClient adminClient,
            String groupId) throws Exception {
        try {
            return adminClient.listConsumerGroupOffsets(groupId)
                    .partitionsToOffsetAndMetadata()
                    .get(10, TimeUnit.SECONDS);
        } catch (ExecutionException ex) {
            if (ex.getCause() instanceof GroupIdNotFoundException) {
                return Map.of();
            }
            throw ex;
        }
    }

    private void startEventListeners() {
        for (String listenerId : LISTENER_IDS) {
            MessageListenerContainer container = listenerRegistry.getListenerContainer(listenerId);
            if (container == null) {
                throw new IllegalStateException("找不到 Kafka Listener：" + listenerId);
            }
            container.start();
        }
    }
}
