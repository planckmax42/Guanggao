package com.example.adplatform.search.candidate.service;

import com.example.adplatform.delivery.request.DeliveryRequest;
import com.example.adplatform.search.port.CandidateSearchPort;
import com.example.adplatform.infra.elasticsearch.CandidateDocument;
import io.micrometer.core.instrument.simple.SimpleMeterRegistry;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

class CandidateRecallServiceTests {

    private StubElasticsearchRecall elasticsearchRecall;
    private StubMysqlRecall mysqlRecall;
    private CandidateRecallService recallService;
    private DeliveryRequest request;

    @BeforeEach
    void setUp() {
        elasticsearchRecall = new StubElasticsearchRecall();
        mysqlRecall = new StubMysqlRecall();
        recallService = new CandidateRecallService(
                elasticsearchRecall, mysqlRecall, new SimpleMeterRegistry());
        request = new DeliveryRequest(
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
        CandidateDocument fallback = new CandidateDocument();
        fallback.setMaterialId(11L);
        elasticsearchRecall.failure = new IllegalStateException("timeout");
        mysqlRecall.result = List.of(fallback);

        CandidateRecallResult result = recallService.recall(request);

        assertThat(result.source()).isEqualTo("MYSQL_FALLBACK");
        assertThat(result.candidates()).extracting(CandidateDocument::getMaterialId).containsExactly(11L);
    }

    @Test
    void shouldUseMysqlDirectlyWhenElasticsearchIsDisabled() {
        elasticsearchRecall.enabled = false;
        mysqlRecall.result = List.of();

        CandidateRecallResult result = recallService.recall(request);

        assertThat(result.source()).isEqualTo("MYSQL_DISABLED");
        assertThat(elasticsearchRecall.calls).isZero();
    }

    private static final class StubElasticsearchRecall implements CandidateSearchPort {
        private List<CandidateDocument> result = List.of();
        private RuntimeException failure;
        private int calls;
        private boolean enabled = true;

        @Override
        public boolean isEnabled() {
            return enabled;
        }

        @Override
        public List<CandidateDocument> recall(DeliveryRequest request) {
            calls++;
            if (failure != null) throw failure;
            return result;
        }
    }

    private static final class StubMysqlRecall extends MysqlCandidateRecallService {
        private List<CandidateDocument> result = List.of();
        private int calls;

        private StubMysqlRecall() {
            super(null, null);
        }

        @Override
        public List<CandidateDocument> recall(DeliveryRequest request) {
            calls++;
            return result;
        }
    }
}
