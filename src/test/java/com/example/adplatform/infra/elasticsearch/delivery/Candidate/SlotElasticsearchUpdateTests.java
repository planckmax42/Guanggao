package com.example.adplatform.infra.elasticsearch.delivery.Candidate;

import com.example.adplatform.common.exception.DependencyException;
import com.example.adplatform.infra.redis.delivery.search.CandidateIndexRebuildGuard;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.MethodSource;
import org.mockito.ArgumentCaptor;
import org.springframework.dao.DataAccessResourceFailureException;
import org.springframework.data.elasticsearch.client.elc.NativeQuery;
import org.springframework.data.elasticsearch.core.ElasticsearchOperations;
import org.springframework.data.elasticsearch.core.RefreshPolicy;
import org.springframework.data.elasticsearch.core.mapping.IndexCoordinates;
import org.springframework.data.elasticsearch.core.query.ByQueryResponse;
import org.springframework.data.elasticsearch.core.query.UpdateQuery;

import java.util.List;
import java.util.stream.Stream;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

class SlotElasticsearchUpdateTests {

    private final ElasticsearchOperations operations = mock(ElasticsearchOperations.class);
    private final CandidateIndexRebuildGuard rebuildGuard = mock(CandidateIndexRebuildGuard.class);
    private final EsProperties properties = new EsProperties();
    private final EsIndexUpdateServiceImpl service = new EsIndexUpdateServiceImpl(
            properties, null, null, operations, rebuildGuard, null);

    @Test
    void updatesOnlyTheSlotCodeOfExactMatchesInTheActiveAlias() {
        properties.getCandidate().setReadAlias("active-candidates");
        when(operations.updateByQuery(any(), any()))
                .thenReturn(ByQueryResponse.builder().withTotal(25).withUpdated(25).build());

        service.SlotElasticSearchUpdate("HOME.TOP", "HOME'NEW");

        ArgumentCaptor<UpdateQuery> captor = ArgumentCaptor.forClass(UpdateQuery.class);
        verify(operations).updateByQuery(captor.capture(), eq(IndexCoordinates.of("active-candidates")));
        UpdateQuery update = captor.getValue();
        NativeQuery query = (NativeQuery) update.getQuery();
        assertThat(query.getQuery().term().field()).isEqualTo("slotCode");
        assertThat(query.getQuery().term().value().stringValue()).isEqualTo("HOME.TOP");
        assertThat(update.getScript()).isEqualTo("ctx._source.slotCode = params.newSlotCode");
        assertThat(update.getParams()).containsEntry("newSlotCode", "HOME'NEW");
        assertThat(update.getRefreshPolicy()).isEqualTo(RefreshPolicy.IMMEDIATE);
        assertThat(update.getMaxDocs()).isNull();
    }

    @Test
    void rejectsConsumptionDuringRebuildWithoutWritingToElasticsearch() {
        when(rebuildGuard.isRebuilding()).thenReturn(true);

        assertThatThrownBy(() -> service.SlotElasticSearchUpdate("OLD", "NEW"))
                .isInstanceOf(DependencyException.class);
        verifyNoInteractions(operations);
    }

    @Test
    void acceptsNoMatchesWhenTheRenameWasAlreadyApplied() {
        when(operations.updateByQuery(any(), any()))
                .thenReturn(ByQueryResponse.builder().withTotal(0).withUpdated(0).build());

        assertThatCode(() -> service.SlotElasticSearchUpdate("OLD", "NEW"))
                .doesNotThrowAnyException();
        verify(operations).updateByQuery(any(), any());
    }

    @ParameterizedTest
    @MethodSource("incompleteUpdates")
    void rejectsIncompleteUpdatesSoTheConsumerCanRetry(ByQueryResponse response) {
        when(operations.updateByQuery(any(), any())).thenReturn(response);

        assertThatThrownBy(() -> service.SlotElasticSearchUpdate("OLD", "NEW"))
                .isInstanceOf(DependencyException.class);
    }

    static Stream<ByQueryResponse> incompleteUpdates() {
        return Stream.of(
                ByQueryResponse.builder().withTimedOut(true).build(),
                ByQueryResponse.builder().withVersionConflicts(1).build(),
                ByQueryResponse.builder().withFailures(List.of(
                        ByQueryResponse.Failure.builder().withStatus(500).build())).build());
    }

    @Test
    void propagatesElasticsearchConnectionFailures() {
        var failure = new DataAccessResourceFailureException("ES unavailable");
        when(operations.updateByQuery(any(), any())).thenThrow(failure);

        assertThatThrownBy(() -> service.SlotElasticSearchUpdate("OLD", "NEW"))
                .isSameAs(failure);
    }
}
