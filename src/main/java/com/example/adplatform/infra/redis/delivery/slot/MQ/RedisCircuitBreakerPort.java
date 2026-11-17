package com.example.adplatform.infra.redis.delivery.slot.MQ;

import io.github.resilience4j.circuitbreaker.CircuitBreaker;
import java.util.function.Supplier;

public interface RedisCircuitBreakerPort {
    <T> T executeSupplier(Supplier<T> supplier);
    void executeRunnable(Runnable runnable);
    CircuitBreaker.State currentState();
}
