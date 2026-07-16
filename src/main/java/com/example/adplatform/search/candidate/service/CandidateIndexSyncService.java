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

/**
 * 消费配置变更后，将单个聚合的最新 MySQL 状态同步到候选写别名。
 *
 * <p>消息只携带聚合类型和 ID，不携带完整配置快照。处理时先删除该聚合的旧文档，再从
 * MySQL 读取当前可投放状态并写回，因此重复消息和过期消息都会收敛到数据库最新状态。
 * 全量重建期间拒绝增量写入，由 Kafka 重试机制稍后重新消费。</p>
 */
@Service
@RequiredArgsConstructor
public class CandidateIndexSyncService {

    private final AdElasticsearchProperties properties;
    private final CandidateSourceMapper sourceMapper;
    private final CandidateDocumentFactory documentFactory;
    private final ElasticsearchOperations operations;
    private final StringRedisTemplate stringRedisTemplate;
    private final DeliveryStopGuardService stopGuardService;

    /**
     * 幂等同步素材、计划、定向规则或广告位的候选文档。
     *
     * @param message 仅包含聚合定位信息的配置变更消息
     */
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
        // 增量消息可能连续到达。必须先 refresh，避免下一次 delete_by_query 搜到旧 Lucene
        // 段并以过期 seq_no 删除，从而产生 409；同时确保清除停投标记前新状态已可搜索。
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
        // 计划与素材是一对多关系，必须按 planId 清理后重新生成该计划的完整候选集合。
        deleteByField("planId", planId, index);
        saveAll(sourceMapper.selectEligibleByPlanId(planId), index);
    }

    private void synchronizeSlot(Long slotId, IndexCoordinates index) {
        // 广告位状态变化会影响该位置下所有素材，不能只更新某一个文档。
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
