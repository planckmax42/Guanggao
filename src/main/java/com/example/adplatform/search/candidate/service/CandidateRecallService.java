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

/**
 * 候选召回入口，封装 ES 主链路、熔断保护、MySQL 降级和监控指标。
 *
 * <p>降级只由调用异常触发。ES 正常返回空列表代表确实没有匹配广告，必须原样返回，
 * 否则回源 MySQL 会放大数据库流量并可能返回与 ES 过滤语义不一致的结果。</p>
 */
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
        // 熔断器只保护 ES 依赖；打开期间请求直接走 MySQL，10 秒后以少量请求探测恢复。
        this.circuitBreaker = CircuitBreaker.of("candidate-es-recall", CircuitBreakerConfig.custom()
                .slidingWindowSize(20)
                .minimumNumberOfCalls(10)
                .failureRateThreshold(50)
                .waitDurationInOpenState(Duration.ofSeconds(10))
                .permittedNumberOfCallsInHalfOpenState(3)
                .build());
    }

    /**
     * 执行一次粗召回，并返回实际数据源以便埋点和压测分析。
     *
     * @param request 投放请求
     * @return 候选列表及 ELASTICSEARCH/MYSQL_* 来源标识
     */
    public CandidateRecallResult recall(AdDeliveryRequest request) {
        long startNanos = System.nanoTime();
        CandidateRecallResult result;
        if (!properties.isEnabled()) {
            result = new CandidateRecallResult(mysqlRecall.recall(request), "MYSQL_DISABLED");
            record(result, startNanos);
            return result;
        }
        try {
            // 空列表也是一次成功调用，不会进入 catch 和 MySQL 降级分支。
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
