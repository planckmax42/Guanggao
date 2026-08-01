package com.example.adplatform.infra.elasticsearch.delivery;

import com.example.adplatform.infra.elasticsearch.config.AdElasticsearchProperties;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.stereotype.Component;

/**
 * 应用启动时以 best-effort 方式初始化 ES 基础设施。
 *
 * <p>候选读别名不存在时才从 MySQL 首次全量建索引。ES 故障不阻断 Spring Boot 启动，
 * 在线投放会由 CandidateRecallService 自动走 MySQL。</p>
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class ElasticsearchBootstrapRunner implements ApplicationRunner {

    private final AdElasticsearchProperties properties;
    private final CandidateIndexManager candidateIndexManager;

    @Override
    public void run(ApplicationArguments args) {
        if (!properties.isEnabled()) {
            return;
        }
        try {
            // 已存在别名时绝不在每次重启后重复全量构建。
            if (!candidateIndexManager.aliasExists()) {
                candidateIndexManager.rebuild();
            }
        } catch (RuntimeException ex) {
            log.warn("Elasticsearch bootstrap failed; delivery will use MySQL fallback", ex);
        }
    }
}
