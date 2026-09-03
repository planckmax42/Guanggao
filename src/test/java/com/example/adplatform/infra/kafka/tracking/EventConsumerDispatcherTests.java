package com.example.adplatform.infra.kafka.tracking;

import com.example.adplatform.common.exception.BusinessException;
import com.example.adplatform.common.exception.DependencyException;
import com.example.adplatform.common.exception.ErrorCode;
import com.example.adplatform.tracking.entity.EventType;
import com.example.adplatform.tracking.message.EventMessage;
import io.micrometer.core.instrument.simple.SimpleMeterRegistry;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.time.LocalDateTime;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertThrows;

class EventConsumerDispatcherTests {

    private SimpleMeterRegistry meterRegistry;
    private EventConsumerDispatcher dispatcher;
    private EventMessage message;

    @BeforeEach
    void setUp() {
        meterRegistry = new SimpleMeterRegistry();
        dispatcher = new EventConsumerDispatcher(meterRegistry);
        message = new EventMessage(
                "event-1",
                "request-1",
                EventType.IMPRESSION,
                "mat_00000000000000000000000000000001",
                2L,
                LocalDateTime.now());
    }

    @Test
    void shouldRecordSuccessMetricsForStage() {
        dispatcher.dispatch(EventConsumerStage.ARCHIVE, message, ignored -> { });

        assertEquals(1D, messageCount("archive", "success"));
        assertEquals(0D, messageCount("archive", "business_error"));
        assertEquals(0D, messageCount("archive", "failure"));
        assertEquals(1L, processingCount("archive"));
        assertEquals(0D, messageCount("billing", "success"));
    }

    @Test
    void shouldRecordAndSwallowBusinessError() {
        dispatcher.dispatch(EventConsumerStage.BILLING, message, ignored -> {
            throw new BusinessException(ErrorCode.RESOURCE_NOT_FOUND);
        });

        assertEquals(0D, messageCount("billing", "success"));
        assertEquals(1D, messageCount("billing", "business_error"));
        assertEquals(0D, messageCount("billing", "failure"));
        assertEquals(1L, processingCount("billing"));
    }

    @Test
    void shouldRecordAndRethrowRuntimeFailure() {
        IllegalStateException failure = new IllegalStateException("database unavailable");

        IllegalStateException thrown = assertThrows(
                IllegalStateException.class,
                () -> dispatcher.dispatch(
                        EventConsumerStage.STATISTICS,
                        message,
                        ignored -> { throw failure; }));

        assertSame(failure, thrown);
        assertEquals(0D, messageCount("statistics", "success"));
        assertEquals(0D, messageCount("statistics", "business_error"));
        assertEquals(1D, messageCount("statistics", "failure"));
        assertEquals(1L, processingCount("statistics"));
    }

    @Test
    void shouldRecordAndRethrowTemporaryDependencyFailure() {
        DependencyException failure = new DependencyException(
                ErrorCode.DEPENDENCY_SERVICE_UNAVAILABLE,
                "event metadata busy");

        DependencyException thrown = assertThrows(
                DependencyException.class,
                () -> dispatcher.dispatch(
                        EventConsumerStage.BILLING,
                        message,
                        ignored -> { throw failure; }));

        assertSame(failure, thrown);
        assertEquals(0D, messageCount("billing", "success"));
        assertEquals(0D, messageCount("billing", "business_error"));
        assertEquals(1D, messageCount("billing", "failure"));
        assertEquals(1L, processingCount("billing"));
    }

    private double messageCount(String stage, String result) {
        return meterRegistry.get("ad.event.consumer.messages")
                .tag("stage", stage)
                .tag("result", result)
                .counter()
                .count();
    }

    private long processingCount(String stage) {
        return meterRegistry.get("ad.event.consumer.processing")
                .tag("stage", stage)
                .timer()
                .count();
    }
}
