package com.example.adplatform.search.candidate.service;

import com.example.adplatform.delivery.dto.AdDeliveryRequest;
import com.example.adplatform.search.config.AdElasticsearchProperties;
import io.github.resilience4j.circuitbreaker.CircuitBreaker;
import io.github.resilience4j.circuitbreaker.CircuitBreakerConfig;
import io.micrometer.core.instrument.MeterRegistry;
import io.micrometer.core.instrument.Timer;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.time.Duration;
import java.util.concurrent.TimeUnit;

@Slf4j
@Service
public class CandidateRecallService {

    private final AdElasticsearchProperties properties;
    private final ElasticsearchCandidateRecallService elasticsearchRecall;
    private final MysqlCandidateRecallService mysqlRecall;
    private final MeterRegistry meterRegistry;
    private final CircuitBreaker circuitBreaker;

    public CandidateRecallService(
            AdElasticsearchProperties properties,
            ElasticsearchCandidateRecallService elasticsearchRecall,
            MysqlCandidateRecallService mysqlRecall,
            MeterRegistry meterRegistry) {
        this.properties = properties;
        this.elasticsearchRecall = elasticsearchRecall;
        this.mysqlRecall = mysqlRecall;
        this.meterRegistry = meterRegistry;
        this.circuitBreaker = CircuitBreaker.of("candidate-es-recall", CircuitBreakerConfig.custom()
                .slidingWindowSize(20)
                .minimumNumberOfCalls(10)
                .failureRateThreshold(50)
                .waitDurationInOpenState(Duration.ofSeconds(10))
                .permittedNumberOfCallsInHalfOpenState(3)
                .build());
    }

    public CandidateRecallResult recall(AdDeliveryRequest request) {
        long startNanos = System.nanoTime();
        CandidateRecallResult result;
        if (!properties.isEnabled()) {
            result = new CandidateRecallResult(mysqlRecall.recall(request), "MYSQL_DISABLED");
            record(result, startNanos);
            return result;
        }
        try {
            result = new CandidateRecallResult(
                    circuitBreaker.executeSupplier(() -> elasticsearchRecall.recall(request)),
                    "ELASTICSEARCH");
        } catch (RuntimeException ex) {
            log.warn("Elasticsearch candidate recall failed; falling back to MySQL, type={}, message={}",
                    ex.getClass().getSimpleName(), ex.getMessage());
            log.debug("Elasticsearch candidate recall failure details", ex);
            meterRegistry.counter("ad.candidate.recall.fallback").increment();
            result = new CandidateRecallResult(mysqlRecall.recall(request), "MYSQL_FALLBACK");
        }
        record(result, startNanos);
        return result;
    }

    private void record(CandidateRecallResult result, long startNanos) {
        String source = result.source();
        Timer.builder("ad.candidate.recall.duration")
                .description("候选粗召回耗时")
                .tag("source", source)
                .register(meterRegistry)
                .record(System.nanoTime() - startNanos, TimeUnit.NANOSECONDS);
        meterRegistry.summary("ad.candidate.recall.size", "source", source)
                .record(result.candidates().size());
    }
}
