package com.example.adplatform.infra.resilience.delivery.slot;

import io.github.resilience4j.circuitbreaker.CallNotPermittedException;
import io.github.resilience4j.circuitbreaker.CircuitBreaker;
import org.junit.jupiter.api.Test;

import java.time.Duration;
import java.util.concurrent.atomic.AtomicBoolean;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;

class SlotMysqlCircuitBreakerTests {

    @Test
    void shouldOpenAfterDatabaseFailuresReachThreshold() {
        SlotMysqlCircuitBreaker circuitBreaker = createCircuitBreaker(Duration.ofSeconds(1));

        assertThrows(IllegalStateException.class,
                () -> circuitBreaker.execute(() -> failedQuery("第一次查询失败")));
        assertThrows(IllegalStateException.class,
                () -> circuitBreaker.execute(() -> failedQuery("第二次查询失败")));

        assertEquals(CircuitBreaker.State.OPEN, circuitBreaker.currentState());
        AtomicBoolean queryExecuted = new AtomicBoolean(false);
        assertThrows(CallNotPermittedException.class, () -> circuitBreaker.execute(() -> {
            queryExecuted.set(true);
            return 1L;
        }));
        assertFalse(queryExecuted.get());
    }

    @Test
    void shouldCountSlowDatabaseQueriesAndOpen() {
        SlotMysqlCircuitBreaker circuitBreaker = createCircuitBreaker(Duration.ofMillis(1));

        circuitBreaker.execute(this::slowQuery);
        circuitBreaker.execute(this::slowQuery);

        assertEquals(CircuitBreaker.State.OPEN, circuitBreaker.currentState());
    }

    private SlotMysqlCircuitBreaker createCircuitBreaker(Duration slowCallThreshold) {
        SlotMysqlCircuitBreakerProperties properties = new SlotMysqlCircuitBreakerProperties();
        properties.setSlidingWindowSize(2);
        properties.setMinimumNumberOfCalls(2);
        properties.setFailureRateThreshold(50F);
        properties.setSlowCallRateThreshold(50F);
        properties.setSlowCallDurationThreshold(slowCallThreshold);
        properties.setOpenStateWaitDuration(Duration.ofSeconds(10));
        properties.setPermittedCallsInHalfOpenState(1);
        return new SlotMysqlCircuitBreaker(properties);
    }

    private Long failedQuery(String message) {
        throw new IllegalStateException(message);
    }

    private Long slowQuery() {
        try {
            Thread.sleep(5L);
            return 1L;
        } catch (InterruptedException ex) {
            Thread.currentThread().interrupt();
            throw new IllegalStateException("模拟慢查询时被中断", ex);
        }
    }
}
