package com.example.adplatform.search.candidate.service;

import com.example.adplatform.delivery.request.AdDeliveryRequest;
import com.example.adplatform.search.port.CandidateSearchPort;
import io.micrometer.core.instrument.MeterRegistry;
import io.micrometer.core.instrument.Timer;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.util.concurrent.TimeUnit;
//todo:该部分已弃用，deliver直接依赖infra层，后续应该迁移此部分功能到业务层
/**
 * 候选召回入口，封装 ES 主链路、熔断保护、MySQL 降级和监控指标。
 *
 * <p>降级只由调用异常触发。ES 正常返回空列表代表确实没有匹配广告，必须原样返回，
 * 否则回源 MySQL 会放大数据库流量并可能返回与 ES 过滤语义不一致的结果。</p>
 */
@Slf4j
@Service
public class CandidateRecallService {

    private final CandidateSearchPort elasticsearchRecall;
    private final MysqlCandidateRecallService mysqlRecall;
    private final MeterRegistry meterRegistry;

    public CandidateRecallService(
            CandidateSearchPort elasticsearchRecall,
            MysqlCandidateRecallService mysqlRecall,
            MeterRegistry meterRegistry) {
        this.elasticsearchRecall = elasticsearchRecall;
        this.mysqlRecall = mysqlRecall;
        this.meterRegistry = meterRegistry;
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
        if (!elasticsearchRecall.isEnabled()) {//ES不可用时降级进入Mysql，todo:后续加入熔断器保护降级策略
            result = new CandidateRecallResult(mysqlRecall.recall(request), "MYSQL_DISABLED");//回源数据库
            record(result, startNanos);//记录指标
            return result;
        }
        try {
            // 空列表也是一次成功调用，不会进入 catch 和 MySQL 降级分支。
            result = new CandidateRecallResult(
                    elasticsearchRecall.recall(request),//在熔断器的保护下进入ES查询
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
