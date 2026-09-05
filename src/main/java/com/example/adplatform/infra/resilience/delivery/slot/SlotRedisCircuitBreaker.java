package com.example.adplatform.infra.resilience.delivery.slot;

import io.github.resilience4j.circuitbreaker.CircuitBreaker;
import io.github.resilience4j.circuitbreaker.CircuitBreakerConfig;
import io.micrometer.core.instrument.Gauge;
import io.micrometer.core.instrument.MeterRegistry;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import java.util.function.Supplier;

/** 共享的广告位 Redis 熔断器，读写故障使用同一健康视图。 */
@Slf4j
@Component
public class SlotRedisCircuitBreaker {

    private final CircuitBreaker circuitBreaker;

    public SlotRedisCircuitBreaker(
            SlotRedisCircuitBreakerProperties properties,
            MeterRegistry meterRegistry) {
        CircuitBreakerConfig config = CircuitBreakerConfig.custom()
                .slidingWindowType(CircuitBreakerConfig.SlidingWindowType.COUNT_BASED)
                .slidingWindowSize(properties.getSlidingWindowSize())
                .minimumNumberOfCalls(properties.getMinimumNumberOfCalls())
                .failureRateThreshold(properties.getFailureRateThreshold())
                .slowCallRateThreshold(properties.getSlowCallRateThreshold())
                .slowCallDurationThreshold(properties.getSlowCallDurationThreshold())
                .waitDurationInOpenState(properties.getOpenStateWaitDuration())
                .permittedNumberOfCallsInHalfOpenState(properties.getPermittedCallsInHalfOpenState())
                .automaticTransitionFromOpenToHalfOpenEnabled(true)
                .build();
        this.circuitBreaker = CircuitBreaker.of("slotRedis", config);
        this.circuitBreaker.getEventPublisher().onStateTransition(event ->
                log.warn("广告位 Redis 熔断器状态变化：{} -> {}",
                        event.getStateTransition().getFromState(),
                        event.getStateTransition().getToState()));
        Gauge.builder("ad.slot.cache.circuit.state", circuitBreaker, SlotRedisCircuitBreaker::stateValue)
                .description("广告位 Redis 熔断器状态：0=CLOSED, 0.5=HALF_OPEN, 1=OPEN")
                .register(meterRegistry);
    }

    public <T> T executeSupplier(Supplier<T> supplier) {
        return circuitBreaker.executeSupplier(supplier);
    }

    CircuitBreaker.State currentState() {
        return circuitBreaker.getState();
    }

    private static double stateValue(CircuitBreaker breaker) {
        return switch (breaker.getState()) {
            case OPEN, FORCED_OPEN -> 1D;
            case HALF_OPEN -> 0.5D;
            default -> 0D;
        };
    }
}
