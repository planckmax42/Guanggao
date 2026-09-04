package com.example.adplatform.infra.resilience.delivery.slot;

import io.github.resilience4j.circuitbreaker.CallNotPermittedException;
import io.github.resilience4j.circuitbreaker.CircuitBreaker;
import io.micrometer.core.instrument.simple.SimpleMeterRegistry;
import org.junit.jupiter.api.Test;
import org.springframework.dao.DataAccessResourceFailureException;

import java.time.Duration;
import java.util.concurrent.atomic.AtomicBoolean;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;

class SlotRedisCircuitBreakerTests {

    @Test
    void shouldOpenAndRejectCallsAfterRedisFailuresReachThreshold() {
        SlotRedisCircuitBreakerProperties properties = new SlotRedisCircuitBreakerProperties();
        properties.setSlidingWindowSize(2);
        properties.setMinimumNumberOfCalls(2);
        properties.setFailureRateThreshold(50F);
        properties.setSlowCallRateThreshold(100F);
        properties.setSlowCallDurationThreshold(Duration.ofSeconds(1));
        properties.setOpenStateWaitDuration(Duration.ofSeconds(10));
        properties.setPermittedCallsInHalfOpenState(1);
        SimpleMeterRegistry registry = new SimpleMeterRegistry();
        SlotRedisCircuitBreaker breaker = new SlotRedisCircuitBreaker(properties, registry);

        assertThrows(DataAccessResourceFailureException.class, () -> breaker.execute(this::failedCall));
        assertThrows(DataAccessResourceFailureException.class, () -> breaker.execute(this::failedCall));

        assertEquals(CircuitBreaker.State.OPEN, breaker.currentState());
        assertEquals(1D, registry.get("ad.slot.cache.circuit.state").gauge().value());
        AtomicBoolean executed = new AtomicBoolean(false);
        assertThrows(CallNotPermittedException.class, () -> breaker.execute(() -> {
            executed.set(true);
            return "unexpected";
        }));
        assertFalse(executed.get());
    }

    private String failedCall() {
        throw new DataAccessResourceFailureException("redis down");
    }
}
