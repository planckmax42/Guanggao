package com.example.adplatform.infra.elasticsearch;

import com.example.adplatform.infra.redis.delivery.search.MQ.CandidateEsRebuildGuard;
import com.example.adplatform.infra.warmup.port.CandidateIndexInitialRebuildPort;
import com.example.adplatform.search.candidate.query.CandidateQueryResult;
import com.example.adplatform.search.candidate.mapper.CandidateSourceMapper;
import com.example.adplatform.search.port.CandidateIndexRebuildPort;
import com.google.common.collect.Lists;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.elasticsearch.core.ElasticsearchOperations;
import org.springframework.data.elasticsearch.core.IndexOperations;
import org.springframework.data.elasticsearch.core.index.AliasAction;
import org.springframework.data.elasticsearch.core.index.AliasActionParameters;
import org.springframework.data.elasticsearch.core.index.AliasActions;
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
    private final CandidateEsRebuildGuard candidateEsRebuildGuard;
    private final CandidateSourceMapper candidateSourceMapper;
    private final CandidateConverter candidateConverter;


    @Override
    public boolean isInitialed() {
         return  elasticsearchOperations
                 .indexOps(IndexCoordinates.of(properties.getCandidate().getReadAlias()))
                 .exists();
    }
    public void regularRebuild() {
        String lockToken = candidateEsRebuildGuard.acquireLock();
        String indexName = "ad-candidate-" + LocalDateTime.now().format(INDEX_SUFFIX);
        try {
            IndexOperations indexOperations = elasticsearchOperations.indexOps(IndexCoordinates.of(indexName));
            indexOperations.create(
                    indexOperations.createSettings(CandidateDocument.class),
                    indexOperations.createMapping(CandidateDocument.class));
            List<CandidateQueryResult> rows = candidateSourceMapper.selectAllCandidateCombinations();//todo：后续应该转换成游标读，防止一次读取撑爆JVM
            for (List<CandidateQueryResult> batch : Lists.partition(rows, 500)) {
                List<CandidateDocument> documents = batch.stream()
                        .map(candidateConverter::queryResultToDocument)
                        .toList();
                elasticsearchOperations.save(documents, IndexCoordinates.of(indexName));
            }
            elasticsearchOperations.indexOps(IndexCoordinates.of(indexName)).refresh();
            String alias = properties.getCandidate().getReadAlias();
            String oldIndex = elasticsearchOperations
                    .indexOps(IndexCoordinates.of(alias))
                    .getAliases(alias)
                    .keySet()
                    .iterator()
                    .next();//todo:什么时候应该catch 什么时候应该不管
            AliasActions actions = new AliasActions();
            actions.add(new AliasAction.Remove(
                    AliasActionParameters.builder()
                            .withIndices(oldIndex)
                            .withAliases(alias)
                            .build()));
            actions.add(new AliasAction.Add(
                    AliasActionParameters.builder()
                            .withIndices(indexName)
                            .withAliases(alias)
                            .build()));
            elasticsearchOperations
                    .indexOps(IndexCoordinates.of(indexName))
                    .alias(actions);
        } finally {
            candidateEsRebuildGuard.releaseLock(lockToken);
        }
    }
}
