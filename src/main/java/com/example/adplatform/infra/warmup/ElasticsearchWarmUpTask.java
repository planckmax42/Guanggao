package com.example.adplatform.infra.warmup;

import com.example.adplatform.infra.elasticsearch.delivery.Candidate.EsProperties;
import com.example.adplatform.infra.elasticsearch.delivery.Candidate.ElasticSearchManager;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

/** 启动时以 best-effort 方式初始化 Elasticsearch 候选索引。 */
@Slf4j
@Component
@RequiredArgsConstructor
public class ElasticsearchWarmUpTask {

    private final EsProperties properties;
    private final ElasticSearchManager elasticSearchManager;

    public void warmUp() {
        if (!properties.isEnabled()) {
            return;
        }
        try {
            elasticSearchManager.initialRebuild();
        } catch (RuntimeException ex) {
            log.warn("Elasticsearch bootstrap failed; delivery will use MySQL fallback", ex);
        }
    }
}
