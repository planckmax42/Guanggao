package com.example.adplatform.infra.elasticsearch;

import com.example.adplatform.infra.redis.delivery.search.CandidateIndexRebuildGuard;
import com.example.adplatform.infra.resilience.delivery.search.CandidateElasticsearchCircuitBreaker;
import org.junit.jupiter.api.Test;
import org.mapstruct.factory.Mappers;
import org.springframework.data.domain.Range;
import java.util.List;
import org.mockito.ArgumentCaptor;
import org.springframework.dao.DataAccessResourceFailureException;
import org.springframework.data.elasticsearch.client.elc.NativeQuery;
import org.springframework.data.elasticsearch.core.ElasticsearchOperations;
import org.springframework.data.elasticsearch.core.IndexOperations;
import org.springframework.data.elasticsearch.core.SearchHits;
import org.springframework.data.elasticsearch.core.SearchHit;
import org.springframework.data.elasticsearch.core.mapping.IndexCoordinates;
import org.springframework.data.elasticsearch.core.query.ByQueryResponse;
import org.springframework.data.elasticsearch.core.query.IndexQuery;
import org.springframework.data.elasticsearch.core.query.UpdateQuery;

import java.util.stream.Stream;

import static org.assertj.core.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

class CandidateOperationServiceImplTests {
    private final ElasticsearchOperations operations = mock(ElasticsearchOperations.class);
    private final CandidateIndexRebuildGuard guard = mock(CandidateIndexRebuildGuard.class);
    private final ElasticSearchProperties properties = new ElasticSearchProperties();
    private final CandidateOperationServiceImpl service = new CandidateOperationServiceImpl(
            properties, operations, guard, new CandidateElasticsearchCircuitBreaker(),
            Mappers.getMapper(CandidateConverter.class));

    @Test
    void createsWithEsGeneratedIdUsingWriteAlias() {
        CreateCandidateRequest request = createRequest(18, 60);
        IndexOperations indexOperations = mock(IndexOperations.class);
        when(operations.indexOps(any(IndexCoordinates.class))).thenReturn(indexOperations);
        when(operations.index(any(IndexQuery.class), any(IndexCoordinates.class))).thenReturn("material-1");

        assertThat(service.create(request)).isEqualTo("material-1");

        ArgumentCaptor<IndexQuery> query = ArgumentCaptor.forClass(IndexQuery.class);
        verify(operations).index(query.capture(), eq(IndexCoordinates.of(properties.getCandidate().getWriteAlias())));
        assertThat(query.getValue().getId()).isNull();
        CandidateDocument document = (CandidateDocument) query.getValue().getObject();
        assertThat(document.getId()).isNull();
        assertThat(document.getMaterialId()).isEqualTo(1L);
        assertThat(document.getSlotId()).isEqualTo(2L);
        assertThat(document.getPlanId()).isEqualTo(3L);
        assertThat(document.getBidPrice()).isEqualTo(100L);
        assertThat(document.getBillingType()).isEqualTo("CPC");
        assertThat(document.getDeviceTypes()).containsExactly("PHONE");
        assertThat(document.getGender()).isEqualTo("ALL");
        assertThat(document.getAgeRange()).isEqualTo(Range.closed(18, 60));
        assertThat(document.getRegions()).containsExactly("110000");
        assertThat(query.getValue().getOpType()).isEqualTo(IndexQuery.OpType.CREATE);
        verify(indexOperations).refresh();
    }

    @Test
    void rejectsCreationDuringRebuild() {
        CreateCandidateRequest request = createRequest(18, 60);
        when(guard.isRebuilding()).thenReturn(true);
        assertThatThrownBy(() -> service.create(request)).isInstanceOf(IllegalStateException.class);
        verifyNoInteractions(operations);
    }

    @Test
    void rejectsNullCreateRequest() {
        assertThatThrownBy(() -> service.create(null)).isInstanceOf(IllegalArgumentException.class);
        verifyNoInteractions(operations);
    }

    @Test
    void convertsUnboundedAgeRanges() {
        var converter = Mappers.getMapper(CandidateConverter.class);
        assertThat(converter.toDocument(createRequest(null, null)).getAgeRange()).isEqualTo(Range.unbounded());
        assertThat(converter.toDocument(createRequest(18, null)).getAgeRange())
                .isEqualTo(Range.rightUnbounded(Range.Bound.inclusive(18)));
        assertThat(converter.toDocument(createRequest(null, 60)).getAgeRange())
                .isEqualTo(Range.leftUnbounded(Range.Bound.inclusive(60)));
    }

    private CreateCandidateRequest createRequest(Integer ageMin, Integer ageMax) {
        return new CreateCandidateRequest(1L, 2L, 3L, 100L, "CPC", List.of("PHONE"),
                "ALL", ageMin, ageMax, List.of("110000"));
    }

    @Test
    void propagatesCreationFailure() {
        CreateCandidateRequest request = createRequest(18, 60);
        var conflict = new DataAccessResourceFailureException("ES unavailable");
        when(operations.index(any(IndexQuery.class), any(IndexCoordinates.class))).thenThrow(conflict);
        assertThatThrownBy(() -> service.create(request)).isSameAs(conflict);
        verify(operations).index(any(IndexQuery.class), any(IndexCoordinates.class));
        verifyNoMoreInteractions(operations);
    }

    @Test
    void retainsConditionalFieldUpdates() {
        IndexOperations indexOperations = mock(IndexOperations.class);
        when(operations.indexOps(any(IndexCoordinates.class))).thenReturn(indexOperations);
        var response = ByQueryResponse.builder().build();
        when(operations.updateByQuery(any(), any())).thenReturn(response);
        assertThat(service.updateBidPrice(10L, "bidPrice", 100L, 200L)).isSameAs(response);
        ArgumentCaptor<UpdateQuery> query = ArgumentCaptor.forClass(UpdateQuery.class);
        verify(operations).updateByQuery(query.capture(), eq(IndexCoordinates.of(properties.getCandidate().getWriteAlias())));
        assertThat(query.getValue().getParams()).containsEntry("oldValue", 100L).containsEntry("newValue", 200L);
        assertThat(((NativeQuery) query.getValue().getQuery()).getQuery().term().field()).isEqualTo("planId");
    }

    @Test
    @SuppressWarnings("unchecked")
    void retainsSevenQueryFiltersAndReadAlias() {
        SearchHits<CandidateDocument> hits = mock(SearchHits.class);
        CandidateDocument document = new CandidateDocument();
        document.setMaterialId(10L);
        document.setSlotId(1L);
        document.setPlanId(20L);
        document.setBidPrice(200L);
        document.setBillingType("CPC");
        document.setGender("ALL");
        document.setRegions(List.of("110000"));
        SearchHit<CandidateDocument> hit = mock(SearchHit.class);
        when(hit.getContent()).thenReturn(document);
        when(hits.stream()).thenReturn(Stream.of(hit));
        when(operations.search(any(NativeQuery.class), eq(CandidateDocument.class), any(IndexCoordinates.class)))
                .thenReturn(hits);
        assertThat(service.query(new QueryCandidateRequest(1L, 100L, "CPC", "PHONE", "MALE", 25, "110000")))
                .containsExactly(new QueryCandidateResponse(10L, 1L, 20L, 200L, "CPC"));
        ArgumentCaptor<NativeQuery> query = ArgumentCaptor.forClass(NativeQuery.class);
        verify(operations).search(query.capture(), eq(CandidateDocument.class),
                eq(IndexCoordinates.of(properties.getCandidate().getReadAlias())));
        assertThat(query.getValue().getSourceFilter()).isNotNull();
        assertThat(query.getValue().getSourceFilter().getIncludes())
                .containsExactly("materialId", "slotId", "planId", "bidPrice", "billingType");
        var filters = query.getValue().getQuery().bool().filter();
        assertThat(filters).hasSize(7);
        assertThat(filters.get(1).range().gt().to(Long.class)).isEqualTo(100L);
        assertThat(filters.get(4).bool().minimumShouldMatch()).isEqualTo("1");
        assertThat(filters.get(5).range().relation().jsonValue()).isEqualTo("contains");
        assertThat(query.getValue().getMaxResults()).isEqualTo(properties.getCandidate().getRecallSize());
    }

    @Test
    void deletesAllMatchesByEachBusinessIdUsingWriteAlias() {
        IndexOperations indexOperations = mock(IndexOperations.class);
        when(operations.indexOps(any(IndexCoordinates.class))).thenReturn(indexOperations);
        var response = ByQueryResponse.builder().withDeleted(250).build();
        when(operations.delete(any(NativeQuery.class), eq(CandidateDocument.class), any(IndexCoordinates.class)))
                .thenReturn(response);

        assertThat(service.deleteByPlanId(10L)).isSameAs(response);
        assertThat(service.deleteByMaterialId(20L)).isSameAs(response);
        assertThat(service.deleteBySlotId(30L)).isSameAs(response);

        ArgumentCaptor<NativeQuery> queries = ArgumentCaptor.forClass(NativeQuery.class);
        verify(operations, times(3)).delete(queries.capture(), eq(CandidateDocument.class),
                eq(IndexCoordinates.of(properties.getCandidate().getWriteAlias())));
        assertThat(queries.getAllValues()).extracting(q -> q.getQuery().term().field())
                .containsExactly("planId", "materialId", "slotId");
        assertThat(queries.getAllValues()).extracting(q -> q.getQuery().term().value().stringValue())
                .containsExactly("10", "20", "30");
        assertThat(queries.getAllValues()).allSatisfy(q -> assertThat(q.getMaxResults()).isNull());
        verify(indexOperations, times(3)).refresh();
    }

    @Test
    void acceptsRepeatedDeletionWithNoRemainingDocuments() {
        when(operations.indexOps(any(IndexCoordinates.class))).thenReturn(mock(IndexOperations.class));
        when(operations.delete(any(NativeQuery.class), eq(CandidateDocument.class), any(IndexCoordinates.class)))
                .thenReturn(ByQueryResponse.builder().withDeleted(1).build())
                .thenReturn(ByQueryResponse.builder().withDeleted(0).build());
        assertThat(service.deleteByPlanId(10L).getDeleted()).isEqualTo(1);
        assertThat(service.deleteByPlanId(10L).getDeleted()).isZero();
    }

    @Test
    void rejectsDeletionDuringRebuildOrWithoutId() {
        assertThatThrownBy(() -> service.deleteByPlanId(null)).isInstanceOf(NullPointerException.class);
        when(guard.isRebuilding()).thenReturn(true);
        assertThatThrownBy(() -> service.deleteByPlanId(10L)).isInstanceOf(IllegalStateException.class);
        assertThatThrownBy(() -> service.deleteByMaterialId(20L)).isInstanceOf(IllegalStateException.class);
        assertThatThrownBy(() -> service.deleteBySlotId(30L)).isInstanceOf(IllegalStateException.class);
        verifyNoInteractions(operations);
    }

    @Test
    void propagatesDeletionFailure() {
        var failure = new DataAccessResourceFailureException("ES unavailable");
        when(operations.delete(any(NativeQuery.class), eq(CandidateDocument.class), any(IndexCoordinates.class)))
                .thenThrow(failure);
        assertThatThrownBy(() -> service.deleteByPlanId(10L)).isSameAs(failure);
        verify(operations).delete(any(NativeQuery.class), eq(CandidateDocument.class), any(IndexCoordinates.class));
        verifyNoMoreInteractions(operations);
    }
}
