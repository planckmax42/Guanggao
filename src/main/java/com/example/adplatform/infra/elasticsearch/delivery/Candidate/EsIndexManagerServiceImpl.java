package com.example.adplatform.infra.elasticsearch.delivery.Candidate;

import co.elastic.clients.elasticsearch.ElasticsearchClient;
import co.elastic.clients.elasticsearch._types.ElasticsearchException;
import co.elastic.clients.elasticsearch._types.mapping.DynamicMapping;
import co.elastic.clients.elasticsearch._types.mapping.Property;
import co.elastic.clients.elasticsearch.indices.update_aliases.Action;
import com.example.adplatform.common.exception.BusinessException;
import com.example.adplatform.common.exception.ErrorCode;
import com.example.adplatform.infra.warmup.port.CandidateIndexInitialRebuildPort;
import com.example.adplatform.infra.warmup.port.exception.CandidateIndexInitialRebuildException;
import com.example.adplatform.search.candidate.query.CandidateSourceRow;
import com.example.adplatform.search.candidate.mapper.CandidateSourceMapper;
import com.example.adplatform.search.candidate.model.AdCandidateDocument;
import com.example.adplatform.search.port.CandidateIndexRebuildPort;
import com.example.adplatform.infra.redis.delivery.search.CandidateIndexRebuildGuard;
import com.example.adplatform.search.port.exception.CandidateIndexRebuildException;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.elasticsearch.core.ElasticsearchOperations;
import org.springframework.data.elasticsearch.core.mapping.IndexCoordinates;
import org.springframework.stereotype.Service;

import java.io.IOException;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;

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
public class EsIndexManagerServiceImpl implements CandidateIndexRebuildPort, CandidateIndexInitialRebuildPort {

    private static final DateTimeFormatter INDEX_SUFFIX = DateTimeFormatter.ofPattern("yyyyMMddHHmmssSSS");

    private final EsProperties properties;
    private final ElasticsearchClient elasticsearchClient;
    private final ElasticsearchOperations elasticsearchOperations;
    private final CandidateIndexRebuildGuard rebuildGuard;
    private final CandidateSourceMapper candidateSourceMapper;
    private final EsDocumentFactory documentFactory;

    /** 判断候选读别名是否已经初始化。ES 不可达时按“不存在”处理并交由启动器降级。 */
    @Override
    public void initialRebuild() {
        if (!properties.isEnabled()) {
            return;
        }
        try {
             boolean aliasExists = elasticsearchClient
                     .indices()
                     .existsAlias(request -> request.name(properties.getCandidate().getReadAlias()))
                     .value();
             if (!aliasExists) {
                 rebuildExecutor();
             }
        } catch (IOException exception) {
            throw new CandidateIndexInitialRebuildException("候选索引初始化重建失败",exception);
        }
    }
     @Override
    public void regularRebuild(){
         try {
             rebuildExecutor();
         } catch (IOException exception) {
             throw new CandidateIndexRebuildException("候选索引重建失败",exception);//todo:清理完成之后回来考虑这个异常怎么处理 定义在哪里合适
         }
     }
    /**
     * 从 MySQL 全量构建新候选索引并原子切换别名。
     *
     * @return 新物理索引名称、写入数量及切换时间
     */

    private void rebuildExecutor() throws IOException{
        if (!properties.isEnabled()) {//检查ES是否启用
            throw new BusinessException(ErrorCode.DEPENDENCY_SERVICE_UNAVAILABLE, "Elasticsearch未启用");
        }
        String lockToken = rebuildGuard.acquire(properties.getCandidate().getRebuildLockTtl());
        String indexName = "ad-candidate-" + LocalDateTime.now().format(INDEX_SUFFIX);//生成新索引名称
        int indexed = 0;
        try {
            // 1. 新建独立物理索引；在别名切换前，在线查询完全不受本次重建影响。
            createIndex(indexName);//这里建立的索引相当于mysql的表，不是倒排索引
            List<CandidateSourceRow> rows = candidateSourceMapper.selectAllEligible();//从mysql中查询出所有数据，此次读取为完全读，todo：后续应该转换成游标读，防止一次读取撑爆JVM
            // 2. 分批写入，控制单次 bulk 请求大小和应用内存占用。
            for (int from = 0; from < rows.size(); from += 500) {//使用Bulk API批量写入，减少网络请求数
                int to = Math.min(from + 500, rows.size());
                List<AdCandidateDocument> documents = rows.subList(from, to).stream()
                        .map(documentFactory::from)
                        .toList();
                elasticsearchOperations.save(documents, IndexCoordinates.of(indexName));
                indexed += documents.size();
            }
            // 3. refresh 后再切别名，确保切换瞬间所有文档已经可搜索。
            elasticsearchOperations.indexOps(IndexCoordinates.of(indexName)).refresh();//确保倒排索引构建完成，手动刷新保证数据的可见性
            switchIndex(indexName);//http请求原子切换
        } finally {
            rebuildGuard.release(lockToken);//最终释放分布式锁
        }
    }

    private void createIndex(String indexName) throws IOException {
        elasticsearchClient.indices().create(request -> request
                .index(indexName)
                .settings(settings -> settings
                        .numberOfShards("1")
                        .numberOfReplicas("0")
                        .refreshInterval(interval -> interval.time("1s")))
                .mappings(mapping -> mapping
                        .dynamic(DynamicMapping.Strict)
                        .properties(candidateProperties())));
    }

    private Map<String, Property> candidateProperties() {
        Map<String, Property> fields = new LinkedHashMap<>();
        keyword(fields, "id", "slotCode", "materialStatus", "auditStatus", "planStatus", "billingType", "gender");
        keyword(fields, "regions", "deviceTypes", "tags");
        longs(fields, "materialId", "planId", "userId", "slotId", "budgetTotal", "budgetDaily", "bidPrice");
        integers(fields, "ageMin", "ageMax");
        booleans(fields, "regionAll", "deviceAll", "genderAll", "ageAll", "tagAll");
        dates(fields, "startTime", "endTime", "updatedAt");
        fields.put("title", nonIndexedKeyword());
        fields.put("description", nonIndexedKeyword());
        fields.put("imageUrl", nonIndexedKeyword());
        fields.put("landingPageUrl", nonIndexedKeyword());
        return fields;
    }

    private void switchIndex(String newIndex) throws IOException {
        Set<String> oldIndices = currentAliasIndices();//得到当前别名对应的物理名称
        List<Action> actions = new ArrayList<>();
        for (String oldIndex : oldIndices) {//拼接移除别名操作，遍历集合中的所有操作
            actions.add(Action.of(action -> action.remove(remove -> remove
                    .index(oldIndex)
                    .aliases(properties.getCandidate().getReadAlias(), properties.getCandidate().getWriteAlias())
                    .mustExist(false))));
        }
        actions.add(Action.of(action -> action.add(add -> add
                .index(newIndex)
                .alias(properties.getCandidate().getReadAlias()))));
        actions.add(Action.of(action -> action.add(add -> add
                .index(newIndex)
                .alias(properties.getCandidate().getWriteAlias())
                .isWriteIndex(true))));
        // 删除旧别名和添加新别名必须放在同一个请求中，避免出现无别名或双写窗口。
        elasticsearchClient.indices().updateAliases(request -> request.actions(actions));
    }

    private Set<String> currentAliasIndices() throws IOException {
        try {
            return elasticsearchClient.indices()
                    .getAlias(request -> request.name(properties.getCandidate().getReadAlias()))
                    .result()
                    .keySet();
        } catch (ElasticsearchException ex) {
            if (ex.status() == 404) {
                return Set.of();
            }
            throw ex;
        }
    }

    private void keyword(Map<String, Property> fields, String... names) {
        for (String name : names) {
            fields.put(name, Property.of(property -> property.keyword(keyword -> keyword)));
        }
    }

    private void longs(Map<String, Property> fields, String... names) {
        for (String name : names) {
            fields.put(name, Property.of(property -> property.long_(longProperty -> longProperty)));
        }
    }

    private void integers(Map<String, Property> fields, String... names) {
        for (String name : names) {
            fields.put(name, Property.of(property -> property.integer(integer -> integer)));
        }
    }

    private void booleans(Map<String, Property> fields, String... names) {
        for (String name : names) {
            fields.put(name, Property.of(property -> property.boolean_(bool -> bool)));
        }
    }

    private void dates(Map<String, Property> fields, String... names) {
        for (String name : names) {
            fields.put(name, Property.of(property -> property.date(date -> date
                    .format("strict_date_optional_time||epoch_millis"))));
        }
    }

    private Property nonIndexedKeyword() {
        return Property.of(property -> property.keyword(keyword -> keyword.index(false)));
    }
}
