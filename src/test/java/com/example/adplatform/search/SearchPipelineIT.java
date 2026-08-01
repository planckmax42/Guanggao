package com.example.adplatform.search;

import com.example.adplatform.search.candidate.model.AdCandidateDocument;
import com.example.adplatform.infra.elasticsearch.delivery.CandidateIndexSyncService;
import com.example.adplatform.infra.redis.delivery.stopguard.DeliveryStopGuardService;
import com.example.adplatform.search.outbox.message.ConfigAggregateType;
import com.example.adplatform.search.outbox.message.ConfigChangeMessage;
import com.fasterxml.jackson.databind.JsonNode;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.web.client.TestRestTemplate;
import org.springframework.data.elasticsearch.core.ElasticsearchOperations;
import org.springframework.data.elasticsearch.core.mapping.IndexCoordinates;
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpMethod;
import org.springframework.http.ResponseEntity;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.kafka.config.KafkaListenerEndpointRegistry;
import org.springframework.kafka.listener.AbstractMessageListenerContainer;
import org.springframework.kafka.listener.DefaultErrorHandler;
import org.springframework.kafka.test.context.EmbeddedKafka;
import org.springframework.test.annotation.DirtiesContext;

import java.time.Duration;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.function.BooleanSupplier;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * 需要本地 MySQL、Redis 和 Elasticsearch；Kafka 由测试内嵌启动。
 * 类名以 IT 结尾，默认 mvn test 不会执行，使用 -Dtest=SearchPipelineIT 显式运行。
 */
@DirtiesContext
@EmbeddedKafka(
        kraft = true,
        partitions = 3,
        topics = {
                "event-topic", "ad-config-change", "ad-config-change-dlt"
        })
@SpringBootTest(
        webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT,
        properties = {
                "spring.kafka.listener.concurrency=1",
                "spring.kafka.consumer.auto-offset-reset=earliest",
                "app.elasticsearch.outbox.transport=polling",
                "app.elasticsearch.outbox.publish-delay-ms=100",
                "app.elasticsearch.outbox.publish-initial-delay-ms=100",
                "app.stats.flush-delay-ms=3600000",
                "app.stats.flush-initial-delay-ms=3600000"
        })
class SearchPipelineIT {

    @Autowired
    private TestRestTemplate restTemplate;
    @Autowired
    private JdbcTemplate jdbcTemplate;
    @Autowired
    private ElasticsearchOperations elasticsearchOperations;
    @Autowired
    private KafkaListenerEndpointRegistry kafkaListenerEndpointRegistry;
    @Autowired
    private CandidateIndexSyncService candidateIndexSyncService;
    @Autowired
    private DeliveryStopGuardService deliveryStopGuardService;

    private long initialOutboxId;
    private boolean outboxSnapshotTaken;
    private final AtomicInteger candidateConsumerRetries = new AtomicInteger();

    @BeforeEach
    void trackCandidateConsumerRetries() {
        kafkaListenerEndpointRegistry.getListenerContainers().forEach(container -> {
            if (container instanceof AbstractMessageListenerContainer<?, ?> listenerContainer
                    && listenerContainer.getCommonErrorHandler() instanceof DefaultErrorHandler errorHandler) {
                errorHandler.setRetryListeners((record, ex, deliveryAttempt) -> {
                    if ("ad-config-change".equals(record.topic())) {
                        candidateConsumerRetries.incrementAndGet();
                    }
                });
            }
        });
    }

    @Test
    void shouldSynchronizeCandidateConfigWithoutRetries() throws Exception {
        initialOutboxId = jdbcTemplate.queryForObject(
                "SELECT COALESCE(MAX(id), 0) FROM outbox_message", Long.class);
        outboxSnapshotTaken = true;
        exerciseCandidateConfigPipeline();
    }

    @AfterEach
    void cleanUp() {
        stopKafkaListeners();
        jdbcTemplate.update("UPDATE plan SET status = 'ONLINE' WHERE id = 1");
        try {
            candidateIndexSyncService.synchronize(new ConfigChangeMessage(
                    UUID.randomUUID().toString(), ConfigAggregateType.PLAN, 1L));
        } catch (RuntimeException ignored) {
            deliveryStopGuardService.mark(ConfigAggregateType.PLAN, 1L, false);
        }
        if (outboxSnapshotTaken) {
            jdbcTemplate.update(
                    "DELETE FROM outbox_message WHERE id > ? AND message_key = 'PLAN:1'", initialOutboxId);
        }
    }

    private void stopKafkaListeners() {
        CountDownLatch stopped = new CountDownLatch(1);
        kafkaListenerEndpointRegistry.stop(stopped::countDown);
        try {
            if (!stopped.await(10, TimeUnit.SECONDS)) {
                throw new IllegalStateException("Kafka listeners did not stop within 10 seconds");
            }
        } catch (InterruptedException ex) {
            Thread.currentThread().interrupt();
            throw new IllegalStateException("Interrupted while stopping Kafka listeners", ex);
        }
    }

    private void exerciseCandidateConfigPipeline() throws Exception {
        ResponseEntity<JsonNode> paused = restTemplate.exchange(
                "/api/admin/plans/1/pause", HttpMethod.PUT, HttpEntity.EMPTY, JsonNode.class);
        assertThat(paused.getStatusCode().is2xxSuccessful()).isTrue();
        assertThat(paused.getBody()).isNotNull();
        assertThat(paused.getBody().path("code").asInt()).isZero();
        await(Duration.ofSeconds(20), () -> !candidateDocumentExists("1"));

        ResponseEntity<JsonNode> online = restTemplate.exchange(
                "/api/admin/plans/1/online", HttpMethod.PUT, HttpEntity.EMPTY, JsonNode.class);
        assertThat(online.getStatusCode().is2xxSuccessful()).isTrue();
        assertThat(online.getBody()).isNotNull();
        assertThat(online.getBody().path("code").asInt()).isZero();
        await(Duration.ofSeconds(20), () -> candidateDocumentExists("1"));
        await(Duration.ofSeconds(5), () ->
                !deliveryStopGuardService.findStoppedPlans(List.of(1L)).contains(1L));

        // 连续配置消息必须保持幂等，不能依赖 Kafka 重试掩盖 ES 近实时版本冲突。
        candidateIndexSyncService.synchronize(new ConfigChangeMessage(
                UUID.randomUUID().toString(), ConfigAggregateType.PLAN, 1L));
        candidateIndexSyncService.synchronize(new ConfigChangeMessage(
                UUID.randomUUID().toString(), ConfigAggregateType.PLAN, 1L));
        assertThat(candidateConsumerRetries).hasValue(0);
    }

    private boolean candidateDocumentExists(String materialId) {
        try {
            return elasticsearchOperations.get(
                    materialId,
                    AdCandidateDocument.class,
                    IndexCoordinates.of("ad-candidate-read")) != null;
        } catch (RuntimeException ex) {
            return false;
        }
    }

    private void await(Duration timeout, BooleanSupplier condition) throws Exception {
        long deadline = System.nanoTime() + timeout.toNanos();
        do {
            if (condition.getAsBoolean()) return;
            Thread.sleep(100L);
        } while (System.nanoTime() < deadline);
        throw new AssertionError("Condition was not satisfied within " + timeout);
    }
}
