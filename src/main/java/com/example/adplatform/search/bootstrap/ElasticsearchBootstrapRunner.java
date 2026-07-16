package com.example.adplatform.search.bootstrap;

import com.example.adplatform.search.candidate.service.CandidateIndexManager;
import com.example.adplatform.search.config.AdElasticsearchProperties;
import com.example.adplatform.search.event.service.EventIndexManager;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.stereotype.Component;

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
            if (!candidateIndexManager.aliasExists()) {
                candidateIndexManager.rebuild();
            }
        } catch (RuntimeException | java.io.IOException ex) {
            log.warn("Elasticsearch bootstrap failed; delivery will use MySQL fallback", ex);
        }
    }
}
