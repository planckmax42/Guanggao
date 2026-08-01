package com.example.adplatform.infra.kafka.tracking;

import com.example.adplatform.common.exception.BusinessException;
import com.example.adplatform.common.exception.ErrorCode;
import com.example.adplatform.infra.resilience.tracking.EventKafkaCircuitBreaker;
import com.example.adplatform.tracking.entity.EventType;
import com.example.adplatform.tracking.message.EventMessage;
import io.micrometer.core.instrument.simple.SimpleMeterRegistry;
import org.apache.kafka.common.errors.SerializationException;
import org.apache.kafka.common.errors.TimeoutException;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.kafka.support.SendResult;
import org.springframework.test.util.ReflectionTestUtils;

import java.time.LocalDateTime;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CompletionException;

import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@SuppressWarnings("unchecked")
class EventKafkaProducerTests {

    private KafkaTemplate<String, EventMessage> kafkaTemplate;
    private SimpleMeterRegistry meterRegistry;
    private EventKafkaProducerProperties properties;

    @BeforeEach
    void setUp() {
        kafkaTemplate = mock(KafkaTemplate.class);
        meterRegistry = new SimpleMeterRegistry();
        properties = new EventKafkaProducerProperties();
        properties.getCircuitBreaker().setSlidingWindowSize(2);
        properties.getCircuitBreaker().setMinimumNumberOfCalls(2);
        properties.getCircuitBreaker().setFailureRateThreshold(50F);
    }

    @Test
    void shouldCompleteOnlyAfterBrokerAcknowledgement() {
        CompletableFuture<SendResult<String, EventMessage>> send =
                new CompletableFuture<>();
        when(kafkaTemplate.send(eq("event-topic"), eq("event-1"), any()))
                .thenReturn(send);
        EventKafkaProducer producer = createProducer();

        CompletableFuture<Void> published =
                producer.publish(message("event-1")).toCompletableFuture();

        assertThat(published).isNotDone();
        send.complete(mock(SendResult.class));
        published.join();

        assertThat(meterRegistry.counter(
                "ad.event.producer.messages", "result", "success").count())
                .isEqualTo(1D);
    }

    @Test
    void shouldMapRetriableFailureToServiceUnavailable() {
        when(kafkaTemplate.send(eq("event-topic"), eq("event-1"), any()))
                .thenReturn(CompletableFuture.failedFuture(
                        new TimeoutException("broker unavailable")));
        EventKafkaProducer producer = createProducer();

        BusinessException failure = failureOf(
                producer.publish(message("event-1")).toCompletableFuture());

        assertThat(failure.getErrorCode())
                .isEqualTo(ErrorCode.DEPENDENCY_SERVICE_UNAVAILABLE);
        assertThat(meterRegistry.counter(
                "ad.event.producer.messages",
                "result",
                "retryable_failure").count()).isEqualTo(1D);
    }

    @Test
    void shouldMapNonRetriableFailureToSystemError() {
        when(kafkaTemplate.send(eq("event-topic"), eq("event-1"), any()))
                .thenReturn(CompletableFuture.failedFuture(
                        new SerializationException("invalid payload")));
        EventKafkaProducer producer = createProducer();

        BusinessException failure = failureOf(
                producer.publish(message("event-1")).toCompletableFuture());

        assertThat(failure.getErrorCode()).isEqualTo(ErrorCode.SYSTEM_ERROR);
        assertThat(meterRegistry.counter(
                "ad.event.producer.messages",
                "result",
                "non_retryable_failure").count()).isEqualTo(1D);
    }

    @Test
    void shouldFailFastWithoutCallingKafkaWhenCircuitIsOpen() {
        when(kafkaTemplate.send(eq("event-topic"), any(), any()))
                .thenReturn(CompletableFuture.failedFuture(
                        new TimeoutException("broker unavailable")));
        EventKafkaCircuitBreaker circuitBreaker =
                new EventKafkaCircuitBreaker(properties, meterRegistry);
        EventKafkaProducer producer = createProducer(circuitBreaker);

        failureOf(producer.publish(message("event-1")).toCompletableFuture());
        failureOf(producer.publish(message("event-2")).toCompletableFuture());
        BusinessException rejected = failureOf(
                producer.publish(message("event-3")).toCompletableFuture());

        assertThat(circuitBreaker.currentState().name()).isEqualTo("OPEN");
        assertThat(rejected.getErrorCode())
                .isEqualTo(ErrorCode.DEPENDENCY_SERVICE_UNAVAILABLE);
        verify(kafkaTemplate, times(2))
                .send(eq("event-topic"), any(), any());
        assertThat(meterRegistry.counter(
                "ad.event.producer.messages",
                "result",
                "circuit_open").count()).isEqualTo(1D);
    }

    @Test
    void shouldConvertSynchronousKafkaFailureToFailedStage() {
        when(kafkaTemplate.send(eq("event-topic"), eq("event-1"), any()))
                .thenThrow(new TimeoutException("metadata unavailable"));
        EventKafkaProducer producer = createProducer();

        BusinessException failure = failureOf(
                producer.publish(message("event-1")).toCompletableFuture());

        assertThat(failure.getErrorCode())
                .isEqualTo(ErrorCode.DEPENDENCY_SERVICE_UNAVAILABLE);
        verify(kafkaTemplate, never())
                .send(eq("another-topic"), any(), any());
    }

    private EventKafkaProducer createProducer() {
        return createProducer(
                new EventKafkaCircuitBreaker(properties, meterRegistry));
    }

    private EventKafkaProducer createProducer(
            EventKafkaCircuitBreaker circuitBreaker) {
        EventKafkaProducer producer =
                new EventKafkaProducer(kafkaTemplate, circuitBreaker, meterRegistry);
        ReflectionTestUtils.setField(producer, "eventTopic", "event-topic");
        return producer;
    }

    private EventMessage message(String eventId) {
        return new EventMessage(
                eventId,
                "request-1",
                EventType.IMPRESSION,
                1L,
                2L,
                LocalDateTime.of(2026, 7, 23, 12, 0));
    }

    private BusinessException failureOf(CompletableFuture<Void> future) {
        CompletionException completionException =
                assertThrows(CompletionException.class, future::join);
        assertThat(completionException.getCause())
                .isInstanceOf(BusinessException.class);
        return (BusinessException) completionException.getCause();
    }
}
