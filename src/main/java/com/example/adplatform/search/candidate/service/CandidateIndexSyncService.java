package com.example.adplatform.search.candidate.service;

import com.example.adplatform.search.candidate.dto.CandidateSourceRow;
import com.example.adplatform.search.candidate.mapper.CandidateSourceMapper;
import com.example.adplatform.search.candidate.model.AdCandidateDocument;
import com.example.adplatform.search.config.AdElasticsearchProperties;
import com.example.adplatform.search.outbox.message.ConfigAggregateType;
import com.example.adplatform.search.outbox.message.ConfigChangeMessage;
import lombok.RequiredArgsConstructor;
import org.springframework.data.elasticsearch.core.ElasticsearchOperations;
import org.springframework.data.elasticsearch.core.mapping.IndexCoordinates;
import org.springframework.data.elasticsearch.core.query.Criteria;
import org.springframework.data.elasticsearch.core.query.CriteriaQuery;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Service;

import java.util.List;

@Service
@RequiredArgsConstructor
public class CandidateIndexSyncService {

    private final AdElasticsearchProperties properties;
    private final CandidateSourceMapper sourceMapper;
    private final CandidateDocumentFactory documentFactory;
    private final ElasticsearchOperations operations;
    private final StringRedisTemplate stringRedisTemplate;
    private final DeliveryStopGuardService stopGuardService;

    public void synchronize(ConfigChangeMessage message) {
        if (Boolean.TRUE.equals(stringRedisTemplate.hasKey(CandidateIndexManager.REBUILDING_KEY))) {
            throw new IllegalStateException("Candidate index is rebuilding");
        }
        IndexCoordinates index = IndexCoordinates.of(properties.getCandidate().getWriteAlias());
        switch (message.aggregateType()) {
            case MATERIAL -> synchronizeMaterial(message.aggregateId(), index);
            case PLAN, RULE -> synchronizePlan(message.aggregateId(), index);
            case SLOT -> synchronizeSlot(message.aggregateId(), index);
        }
        operations.indexOps(index).refresh();
        stopGuardService.mark(message.aggregateType(), message.aggregateId(), false);
    }

    private void synchronizeMaterial(Long materialId, IndexCoordinates index) {
        operations.delete(String.valueOf(materialId), index);
        CandidateSourceRow row = sourceMapper.selectEligibleByMaterialId(materialId);
        if (row != null) {
            operations.save(documentFactory.from(row), index);
        }
    }

    private void synchronizePlan(Long planId, IndexCoordinates index) {
        deleteByField("planId", planId, index);
        saveAll(sourceMapper.selectEligibleByPlanId(planId), index);
    }

    private void synchronizeSlot(Long slotId, IndexCoordinates index) {
        deleteByField("slotId", slotId, index);
        saveAll(sourceMapper.selectEligibleBySlotId(slotId), index);
    }

    private void deleteByField(String field, Long value, IndexCoordinates index) {
        operations.delete(new CriteriaQuery(new Criteria(field).is(value)), AdCandidateDocument.class, index);
    }

    private void saveAll(List<CandidateSourceRow> rows, IndexCoordinates index) {
        if (!rows.isEmpty()) {
            operations.save(rows.stream().map(documentFactory::from).toList(), index);
        }
    }
}
