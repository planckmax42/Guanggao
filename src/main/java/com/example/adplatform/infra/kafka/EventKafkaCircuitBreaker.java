package com.example.adplatform.infra.kafka;

import io.github.resilience4j.circuitbreaker.CircuitBreaker;
import io.github.resilience4j.circuitbreaker.CircuitBreakerConfig;
import io.micrometer.core.instrument.Gauge;
import io.micrometer.core.instrument.MeterRegistry;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import java.util.concurrent.CompletionStage;
import java.util.function.Supplier;

/**
 * 对异步 Kafka 发送结果计数，在持续故障时快速拒绝新的事件发送。
 */
@Slf4j
@Component
public class EventKafkaCircuitBreaker {

    private final CircuitBreaker circuitBreaker;

    public EventKafkaCircuitBreaker(
            EventKafkaProducerProperties properties,
            MeterRegistry meterRegistry) {
        EventKafkaProducerProperties.CircuitBreakerSettings settings =
                properties.getCircuitBreaker();
        CircuitBreakerConfig config = CircuitBreakerConfig.custom()
                .slidingWindowType(CircuitBreakerConfig.SlidingWindowType.COUNT_BASED)
                .slidingWindowSize(settings.getSlidingWindowSize())
                .minimumNumberOfCalls(settings.getMinimumNumberOfCalls())
                .failureRateThreshold(settings.getFailureRateThreshold())
                .waitDurationInOpenState(settings.getOpenStateWaitDuration())
                .permittedNumberOfCallsInHalfOpenState(
                        settings.getPermittedCallsInHalfOpenState())
                .build();
        this.circuitBreaker = CircuitBreaker.of("eventKafkaProducer", config);
        this.circuitBreaker.getEventPublisher().onStateTransition(event ->
                log.warn("事件 Kafka Producer 熔断器状态变化：{} -> {}",
                        event.getStateTransition().getFromState(),
                        event.getStateTransition().getToState()));
        Gauge.builder("ad.event.producer.circuit.open", circuitBreaker,
                        breaker -> breaker.getState() == CircuitBreaker.State.OPEN ? 1D : 0D)
                .description("Whether the event Kafka producer circuit breaker is open")
                .register(meterRegistry);
    }

    public <T> CompletionStage<T> execute(
            Supplier<CompletionStage<T>> operation) {
        return circuitBreaker.executeCompletionStage(operation);
    }

    CircuitBreaker.State currentState() {
        return circuitBreaker.getState();
    }
}
