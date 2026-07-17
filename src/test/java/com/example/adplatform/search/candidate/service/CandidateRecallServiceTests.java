package com.example.adplatform.search.candidate.service;

import com.example.adplatform.delivery.request.AdDeliveryRequest;
import com.example.adplatform.search.candidate.model.AdCandidateDocument;
import com.example.adplatform.search.config.AdElasticsearchProperties;
import io.micrometer.core.instrument.simple.SimpleMeterRegistry;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

class CandidateRecallServiceTests {

    private AdElasticsearchProperties properties;
    private StubElasticsearchRecall elasticsearchRecall;
    private StubMysqlRecall mysqlRecall;
    private CandidateRecallService recallService;
    private AdDeliveryRequest request;

    @BeforeEach
    void setUp() {
        properties = new AdElasticsearchProperties();
        elasticsearchRecall = new StubElasticsearchRecall();
        mysqlRecall = new StubMysqlRecall();
        recallService = new CandidateRecallService(
                properties, elasticsearchRecall, mysqlRecall, new SimpleMeterRegistry());
        request = new AdDeliveryRequest(
                1001L, "HOME_BANNER", "BEIJING", "IOS", 28, "FEMALE", List.of("fresh"), 3);
    }

    @Test
    void shouldNotFallBackWhenElasticsearchLegitimatelyReturnsNoCandidate() {
        elasticsearchRecall.result = List.of();

        CandidateRecallResult result = recallService.recall(request);

        assertThat(result.source()).isEqualTo("ELASTICSEARCH");
        assertThat(result.candidates()).isEmpty();
        assertThat(mysqlRecall.calls).isZero();
    }

    @Test
    void shouldFallBackToMysqlWhenElasticsearchThrows() {
        AdCandidateDocument fallback = new AdCandidateDocument();
        fallback.setMaterialId(11L);
        elasticsearchRecall.failure = new IllegalStateException("timeout");
        mysqlRecall.result = List.of(fallback);

        CandidateRecallResult result = recallService.recall(request);

        assertThat(result.source()).isEqualTo("MYSQL_FALLBACK");
        assertThat(result.candidates()).extracting(AdCandidateDocument::getMaterialId).containsExactly(11L);
    }

    @Test
    void shouldUseMysqlDirectlyWhenElasticsearchIsDisabled() {
        properties.setEnabled(false);
        mysqlRecall.result = List.of();

        CandidateRecallResult result = recallService.recall(request);

        assertThat(result.source()).isEqualTo("MYSQL_DISABLED");
        assertThat(elasticsearchRecall.calls).isZero();
    }

    private static final class StubElasticsearchRecall extends ElasticsearchCandidateRecallService {
        private List<AdCandidateDocument> result = List.of();
        private RuntimeException failure;
        private int calls;

        private StubElasticsearchRecall() {
            super(null, null, null);
        }

        @Override
        public List<AdCandidateDocument> recall(AdDeliveryRequest request) {
            calls++;
            if (failure != null) throw failure;
            return result;
        }
    }

    private static final class StubMysqlRecall extends MysqlCandidateRecallService {
        private List<AdCandidateDocument> result = List.of();
        private int calls;

        private StubMysqlRecall() {
            super(null, null);
        }

        @Override
        public List<AdCandidateDocument> recall(AdDeliveryRequest request) {
            calls++;
            return result;
        }
    }
}
