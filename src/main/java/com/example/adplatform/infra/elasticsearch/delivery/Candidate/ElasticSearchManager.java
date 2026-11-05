package com.example.adplatform.infra.elasticsearch.delivery.Candidate;

import com.example.adplatform.common.exception.BusinessException;
import com.example.adplatform.common.exception.DependencyException;
import com.example.adplatform.common.exception.ErrorCode;
import com.example.adplatform.infra.warmup.port.CandidateIndexInitialRebuildPort;
import com.example.adplatform.search.candidate.query.CandidateQueryResult;
import com.example.adplatform.search.candidate.mapper.CandidateSourceMapper;
import com.example.adplatform.search.candidate.model.CandidateDocument;
import com.example.adplatform.search.port.CandidateIndexRebuildPort;
import com.example.adplatform.infra.redis.delivery.search.CandidateIndexRebuildGuard;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.google.common.collect.Lists;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.elasticsearch.core.ElasticsearchOperations;
import org.springframework.data.elasticsearch.core.IndexOperations;
import org.springframework.data.elasticsearch.core.index.AliasAction;
import org.springframework.data.elasticsearch.core.index.AliasActionParameters;
import org.springframework.data.elasticsearch.core.index.AliasActions;
import org.springframework.data.domain.Range;
import org.springframework.data.elasticsearch.core.mapping.IndexCoordinates;
import org.springframework.stereotype.Service;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.List;

/**
 * 候选索引的全量重建与别名切换管理器。
 *
 * <p>每次重建创建新的版本化物理索引，完成全量写入和 refresh 后，使用一次
 * {@code _aliases} 请求原子切换读写别名。旧物理索引不会自动删除，便于人工回滚。
 * Redis 锁阻止多个实例并发重建，rebuilding 标记则让增量消费者暂缓写入。</p>
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class ElasticSearchManager implements CandidateIndexRebuildPort, CandidateIndexInitialRebuildPort {

    private static final DateTimeFormatter INDEX_SUFFIX = DateTimeFormatter.ofPattern("yyyyMMddHHmmssSSS");

    private final EsProperties properties;
    private final ElasticsearchOperations elasticsearchOperations;
    private final CandidateIndexRebuildGuard rebuildGuard;
    private final CandidateSourceMapper candidateSourceMapper;
    private final ObjectMapper objectMapper;
    @Override
    public void initialRebuild() {
         boolean aliasExists = elasticsearchOperations
                 .indexOps(IndexCoordinates.of(properties.getCandidate().getReadAlias()))
                 .exists();
         if (!aliasExists) {
             rebuildExecutor();
         }
    }
     @Override
    public void regularRebuild(){
             rebuildExecutor();
     }
    private void rebuildExecutor() {
        if (!properties.isEnabled()) {
            throw new DependencyException(ErrorCode.DEPENDENCY_SERVICE_UNAVAILABLE, "Elasticsearch未启用");
        }
        String lockToken = rebuildGuard.acquire(properties.getCandidate().getRebuildLockTtl());
        String indexName = "ad-candidate-" + LocalDateTime.now().format(INDEX_SUFFIX);
        try {
            createIndex(indexName);
            List<CandidateQueryResult> rows = candidateSourceMapper.selectAllCandidateCombinations();//todo：后续应该转换成游标读，防止一次读取撑爆JVM
            for (List<CandidateQueryResult> batch : Lists.partition(rows, 500)) {
                List<CandidateDocument> documents = batch.stream()
                        .map(this::fromQueryResultToDocument)
                        .toList();
                elasticsearchOperations.save(documents, IndexCoordinates.of(indexName));
            }
            elasticsearchOperations.indexOps(IndexCoordinates.of(indexName)).refresh();
            switchIndex(indexName);
        } finally {
            rebuildGuard.release(lockToken);
        }
    }

    private void createIndex(String indexName) {
        IndexOperations indexOps = elasticsearchOperations.indexOps(IndexCoordinates.of(indexName));
        indexOps.create(
                indexOps.createSettings(CandidateDocument.class),
                indexOps.createMapping(CandidateDocument.class));
    }

    private void switchIndex(String newIndex)  {
        String alias = properties.getCandidate().getReadAlias();
        String oldIndex = currentAliasIndex();
        AliasActions actions = new AliasActions();
        actions.add(new AliasAction.Remove(
                AliasActionParameters.builder()
                        .withIndices(oldIndex)
                        .withAliases(alias)
                        .build()));
        actions.add(new AliasAction.Add(
                AliasActionParameters.builder()
                        .withIndices(newIndex)
                        .withAliases(alias)
                        .build()));
        elasticsearchOperations
                .indexOps(IndexCoordinates.of(newIndex))
                .alias(actions);
    }
    private String currentAliasIndex() {
        String alias = properties.getCandidate().getReadAlias();
        return elasticsearchOperations
                .indexOps(IndexCoordinates.of(alias))
                .getAliases(alias)
                .keySet()
                .iterator()
                .next();//todo:什么时候应该catch 什么时候应该不管
    }

    CandidateDocument fromQueryResultToDocument(CandidateQueryResult result){
        CandidateDocument document = new CandidateDocument();
        document.setId(result.getPlanPublicId() + ":" + result.getMaterialPublicId() + ":"
                + (result.getRulePublicId() == null ? "" : result.getRulePublicId()));
        document.setAdvertiserPublicId(result.getAdvertiserPublicId());
        document.setRulePublicId(result.getRulePublicId());
        document.setDeviceType(fromJsonToList(result.getDeviceType()));
        document.setGender(result.getGender());
        document.setAgeRange(Range.of(
                result.getAgeMin() == null ? Range.Bound.unbounded() : Range.Bound.inclusive(result.getAgeMin()),
                result.getAgeMax() == null ? Range.Bound.unbounded() : Range.Bound.inclusive(result.getAgeMax())));
        document.setRegionCode(fromJsonToList(result.getRegionCode()));
        document.setPlanPublicId(result.getPlanPublicId());
        document.setBidPrice(result.getBidPrice());
        document.setBillingType(result.getBillingType());
        document.setMaterialPublicId(result.getMaterialPublicId());
        document.setSlotPublicId(result.getSlotPublicId());
        document.setSlotCode(result.getSlotCode());
        return document;
    }

    private List<String> fromJsonToList(String json) {
        try {
            return objectMapper.readValue(json, new TypeReference<>() {});
        } catch (JsonProcessingException ex) {
            throw new BusinessException(ErrorCode.SERIALIZATION_FAILED,"JSON类型转化失败",ex);
        }
    }
}
