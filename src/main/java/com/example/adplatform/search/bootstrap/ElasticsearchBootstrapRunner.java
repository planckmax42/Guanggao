package com.example.adplatform.search.bootstrap;

import com.example.adplatform.search.candidate.service.CandidateIndexManager;
import com.example.adplatform.search.config.AdElasticsearchProperties;
import com.example.adplatform.search.event.service.EventIndexManager;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.stereotype.Component;

/**
 * 应用启动时以 best-effort 方式初始化 ES 基础设施。
 *
 * <p>先安装事件模板和 ILM；候选读别名不存在时才从 MySQL 首次全量建索引。ES 故障
 * 不阻断 Spring Boot 启动，在线投放会由 CandidateRecallService 自动走 MySQL。</p>
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class ElasticsearchBootstrapRunner implements ApplicationRunner {

    private final AdElasticsearchProperties properties;
    private final CandidateIndexManager candidateIndexManager;
    private final EventIndexManager eventIndexManager;

    @Override
    public void run(ApplicationArguments args) {
        if (!properties.isEnabled()) {
            return;
        }
        try {
            eventIndexManager.ensureTemplate();
            // 已存在别名时绝不在每次重启后重复全量构建。
            if (!candidateIndexManager.aliasExists()) {
                candidateIndexManager.rebuild();
            }
        } catch (RuntimeException | java.io.IOException ex) {
            log.warn("Elasticsearch bootstrap failed; delivery will use MySQL fallback", ex);
        }
    }
}
