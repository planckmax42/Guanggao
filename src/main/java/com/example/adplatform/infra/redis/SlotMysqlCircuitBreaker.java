package com.example.adplatform.infra.redis;

import io.github.resilience4j.circuitbreaker.CircuitBreaker;
import io.github.resilience4j.circuitbreaker.CircuitBreakerConfig;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import java.util.function.Supplier;

/**
 * 保护广告位缓存回源 MySQL 的熔断器。
 */
@Slf4j
@Component
public class SlotMysqlCircuitBreaker {

    private final CircuitBreaker circuitBreaker;

    public SlotMysqlCircuitBreaker(SlotCacheProperties properties) {
        SlotCacheProperties.MysqlCircuitBreaker config = properties.getMysqlCircuitBreaker();
        CircuitBreakerConfig circuitBreakerConfig = CircuitBreakerConfig.custom()
                .slidingWindowType(CircuitBreakerConfig.SlidingWindowType.COUNT_BASED)
                .slidingWindowSize(config.getSlidingWindowSize())
                .minimumNumberOfCalls(config.getMinimumNumberOfCalls())
                .failureRateThreshold(config.getFailureRateThreshold())
                .slowCallRateThreshold(config.getSlowCallRateThreshold())
                .slowCallDurationThreshold(config.getSlowCallDurationThreshold())
                .waitDurationInOpenState(config.getOpenStateWaitDuration())
                .permittedNumberOfCallsInHalfOpenState(config.getPermittedCallsInHalfOpenState())
                .automaticTransitionFromOpenToHalfOpenEnabled(true)
                .build();

        this.circuitBreaker = CircuitBreaker.of("slotMysqlFallback", circuitBreakerConfig);
        this.circuitBreaker.getEventPublisher().onStateTransition(event ->
                log.warn("广告位 MySQL 回源熔断器状态变化：{} -> {}",
                        event.getStateTransition().getFromState(),
                        event.getStateTransition().getToState()));
    }

    public <T> T execute(Supplier<T> supplier) {
        return circuitBreaker.executeSupplier(supplier);
    }

    public CircuitBreaker.State currentState() {
        return circuitBreaker.getState();
    }
}
