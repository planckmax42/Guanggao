package com.example.adplatform.infra.resilience.delivery.search;

import io.github.resilience4j.circuitbreaker.CircuitBreaker;
import io.github.resilience4j.circuitbreaker.CircuitBreakerConfig;
import org.springframework.stereotype.Component;

import java.time.Duration;
import java.util.function.Supplier;

/** ES 候选召回熔断器。 */
@Component
public class CandidateElasticsearchCircuitBreaker {

    private final CircuitBreaker circuitBreaker;

    public CandidateElasticsearchCircuitBreaker() {
        // 熔断器只保护 ES 依赖；打开期间请求直接走 MySQL，10 秒后以少量请求探测恢复。
        this.circuitBreaker = CircuitBreaker.of(
                "candidate-es-recall",
                CircuitBreakerConfig.custom()//此处熔断器配置直接在代码里面，todo：后续考虑像mysql熔断器一样配置成单独文件
                        .slidingWindowSize(20)
                        .minimumNumberOfCalls(10)
                        .failureRateThreshold(50)
                        .waitDurationInOpenState(Duration.ofSeconds(10))
                        .permittedNumberOfCallsInHalfOpenState(3)
                        .build());
    }

    public <T> T execute(Supplier<T> operation) {
        return circuitBreaker.executeSupplier(operation);
    }
}
