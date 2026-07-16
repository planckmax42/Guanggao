package com.example.adplatform.search;

import com.example.adplatform.infra.redis.RedisKeyConstants;
import com.example.adplatform.search.candidate.model.AdCandidateDocument;
import com.example.adplatform.search.candidate.service.CandidateIndexSyncService;
import com.example.adplatform.search.candidate.service.DeliveryStopGuardService;
import com.example.adplatform.search.event.model.AdEventDocument;
import com.example.adplatform.search.event.service.EventIndexManager;
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
import org.springframework.data.redis.core.StringRedisTemplate;
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
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
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
                "event-topic", "ad-config-change", "ad-event-index",
                "ad-config-change-dlt", "ad-event-index-dlt"
        })
@SpringBootTest(
        webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT,
        properties = {
                "spring.kafka.listener.concurrency=1",
                "spring.kafka.consumer.auto-offset-reset=earliest",
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
    private StringRedisTemplate redisTemplate;
    @Autowired
    private ElasticsearchOperations elasticsearchOperations;
    @Autowired
    private EventIndexManager eventIndexManager;
    @Autowired
    private KafkaListenerEndpointRegistry kafkaListenerEndpointRegistry;
    @Autowired
    private CandidateIndexSyncService candidateIndexSyncService;
    @Autowired
    private DeliveryStopGuardService deliveryStopGuardService;

    private final List<String> eventIds = new ArrayList<>();
    private String requestId;
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
    void shouldSynchronizeConfigAndIndexEventsWithSearchAfter() throws Exception {
        initialOutboxId = jdbcTemplate.queryForObject(
                "SELECT COALESCE(MAX(id), 0) FROM outbox_message", Long.class);
        outboxSnapshotTaken = true;
        exerciseCandidateConfigPipeline();

        requestId = "it-request-" + UUID.randomUUID();
        eventIds.add("it-event-" + UUID.randomUUID());
        eventIds.add("it-event-" + UUID.randomUUID());

        for (int i = 0; i < eventIds.size(); i++) {
            Map<String, Object> body = Map.of(
                    "eventId", eventIds.get(i),
                    "requestId", requestId,
                    "eventType", "CONVERSION",
                    "materialId", 3L,
                    "viewerId", 990000L + i);
            ResponseEntity<JsonNode> response = restTemplate.postForEntity(
                    "/api/tracking/events", body, JsonNode.class);
            assertThat(response.getStatusCode().is2xxSuccessful()).isTrue();
            assertThat(response.getBody()).isNotNull();
            assertThat(response.getBody().path("code").asInt()).isZero();
        }

        await(Duration.ofSeconds(20), () -> countEvents() == 2L);
        await(Duration.ofSeconds(20), () -> countSentEventOutbox() == 2L);
        await(Duration.ofSeconds(20), this::bothDocumentsExist);

        JsonNode firstPage = awaitSearchPage(true, null);
        String firstEventId = firstPage.path("data").path("records").get(0).path("eventId").asText();
        String cursor = firstPage.path("data").path("nextCursor").asText();
        assertThat(cursor).isNotBlank();

        JsonNode secondPage = awaitSearchPage(false, cursor);
        String secondEventId = secondPage.path("data").path("records").get(0).path("eventId").asText();
        assertThat(secondEventId).isNotEqualTo(firstEventId);
        assertThat(eventIds).containsExactlyInAnyOrder(firstEventId, secondEventId);
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
        LocalDate today = LocalDate.now();
        IndexCoordinates index = IndexCoordinates.of(eventIndexManager.indexName(today));
        for (String eventId : eventIds) {
            try {
                elasticsearchOperations.delete(eventId, index);
            } catch (RuntimeException ignored) {
                // 验证失败时尽量清理已写入的部分数据。
            }
            jdbcTemplate.update("DELETE FROM outbox_message WHERE message_key = ?", eventId);
            jdbcTemplate.update("DELETE FROM charge_record WHERE event_id = ?", eventId);
            jdbcTemplate.update("DELETE FROM `event` WHERE event_id = ?", eventId);
        }
        try {
            elasticsearchOperations.indexOps(index).refresh();
        } catch (RuntimeException ignored) {
            // 日索引尚未创建时无需 refresh。
        }
        String statsKey = RedisKeyConstants.dailyStats(today, 2L, 3L, 1L);
        redisTemplate.delete(statsKey);
        redisTemplate.opsForSet().remove(RedisKeyConstants.dailyStatsDirtySet(today), statsKey);
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

    private long countEvents() {
        Long count = jdbcTemplate.queryForObject(
                "SELECT COUNT(*) FROM `event` WHERE request_id = ?", Long.class, requestId);
        return count == null ? 0L : count;
    }

    private long countSentEventOutbox() {
        Long count = jdbcTemplate.queryForObject(
                "SELECT COUNT(*) FROM outbox_message WHERE topic = 'ad-event-index' "
                        + "AND message_key IN (?, ?) AND status = 'SENT'",
                Long.class, eventIds.get(0), eventIds.get(1));
        return count == null ? 0L : count;
    }

    private boolean bothDocumentsExist() {
        IndexCoordinates index = IndexCoordinates.of(eventIndexManager.indexName(LocalDate.now()));
        try {
            return eventIds.stream().allMatch(eventId ->
                    elasticsearchOperations.get(eventId, AdEventDocument.class, index) != null);
        } catch (RuntimeException indexNotReady) {
            return false;
        }
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

    private JsonNode awaitSearchPage(boolean expectMore, String cursor) throws Exception {
        long deadline = System.nanoTime() + Duration.ofSeconds(20).toNanos();
        JsonNode last = null;
        do {
            String url = "/api/search/events?requestId=" + requestId + "&size=1"
                    + (cursor == null ? "" : "&cursor=" + cursor);
            ResponseEntity<JsonNode> response = restTemplate.getForEntity(url, JsonNode.class);
            last = response.getBody();
            if (response.getStatusCode().is2xxSuccessful()
                    && last != null
                    && last.path("code").asInt(-1) == 0
                    && last.path("data").path("records").size() == 1
                    && last.path("data").path("hasMore").asBoolean() == expectMore) {
                return last;
            }
            Thread.sleep(200L);
        } while (System.nanoTime() < deadline);
        throw new AssertionError("Event search page was not ready: " + last);
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
