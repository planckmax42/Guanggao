package com.example.adplatform.infra.elasticsearch.delivery.Candidate;

import co.elastic.clients.elasticsearch.ElasticsearchClient;
import co.elastic.clients.elasticsearch._types.mapping.DynamicMapping;
import co.elastic.clients.elasticsearch._types.mapping.KeywordProperty;
import co.elastic.clients.elasticsearch._types.mapping.Property;
import co.elastic.clients.elasticsearch.indices.update_aliases.Action;
import com.example.adplatform.common.exception.BusinessException;
import com.example.adplatform.common.exception.DependencyException;
import com.example.adplatform.common.exception.ErrorCode;
import com.example.adplatform.infra.warmup.port.CandidateIndexInitialRebuildPort;
import com.example.adplatform.infra.warmup.port.exception.CandidateIndexInitialRebuildException;
import com.example.adplatform.search.candidate.query.CandidateQueryResult;
import com.example.adplatform.search.candidate.mapper.CandidateSourceMapper;
import com.example.adplatform.search.candidate.model.CandidateDocument;
import com.example.adplatform.search.port.CandidateIndexRebuildPort;
import com.example.adplatform.infra.redis.delivery.search.CandidateIndexRebuildGuard;
import com.example.adplatform.search.port.exception.CandidateIndexRebuildException;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.elasticsearch.NoSuchIndexException;
import org.springframework.data.elasticsearch.ResourceNotFoundException;
import org.springframework.data.elasticsearch.core.ElasticsearchOperations;
import org.springframework.data.elasticsearch.core.IndexOperations;
import org.springframework.data.elasticsearch.core.index.AliasAction;
import org.springframework.data.elasticsearch.core.index.AliasActionParameters;
import org.springframework.data.elasticsearch.core.index.AliasActions;
import org.springframework.data.domain.Range;
import org.springframework.data.elasticsearch.core.mapping.IndexCoordinates;
import org.springframework.stereotype.Service;

import java.io.IOException;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

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
    private final ObjectMapper objectMapper;

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
            throw new DependencyException(ErrorCode.DEPENDENCY_SERVICE_UNAVAILABLE, "Elasticsearch未启用");
        }
        String lockToken = rebuildGuard.acquire(properties.getCandidate().getRebuildLockTtl());
        String indexName = "ad-candidate-" + LocalDateTime.now().format(INDEX_SUFFIX);//生成新索引名称
        try {
            // 1. 新建独立物理索引；在别名切换前，在线查询完全不受本次重建影响。
            createIndex(indexName);//这里建立的索引相当于mysql的表，不是倒排索引
            List<CandidateQueryResult> rows = candidateSourceMapper.selectAllEligible();//从mysql中查询出所有数据，此次读取为完全读，todo：后续应该转换成游标读，防止一次读取撑爆JVM
            // 2. 分批写入，控制单次 bulk 请求大小和应用内存占用。
            for (int from = 0; from < rows.size(); from += 500) {//使用Bulk API批量写入，减少网络请求数
                int to = Math.min(from + 500, rows.size());
                List<CandidateDocument> documents = rows.subList(from, to).stream()
                        .map(documentFactory::from)
                        .toList();
                elasticsearchOperations.save(documents, IndexCoordinates.of(indexName));
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
                        .numberOfShards("2")
                        .numberOfReplicas("1")
                        .refreshInterval(interval -> interval.time("1s")))
                .mappings(mapping -> mapping
                        .dynamic(DynamicMapping.Strict)
                        .properties(candidateProperties())));
    }

    private void createIndexWithSpringData(String indexName) {
        IndexOperations indexOps = elasticsearchOperations.indexOps(IndexCoordinates.of(indexName));
        indexOps.create(
                indexOps.createSettings(CandidateDocument.class),
                indexOps.createMapping(CandidateDocument.class)
        );
    }

    private Map<String, Property> candidateProperties() {
        Map<String, Property> fields = new LinkedHashMap<>();//todo:系统的学习一下map
        keyword(fields, "id", "materialPublicId", "planPublicId", "slotPublicId", "slotCode",
                "materialStatus", "auditStatus", "planStatus", "billingType", "gender");
        keyword(fields, "regions", "deviceTypes", "tags");
        longs(fields, "materialId", "planId", "advertiserId", "slotId", "budgetTotal", "budgetDaily", "bidPrice");
        integers(fields, "ageMin", "ageMax");
        booleans(fields, "regionAll", "deviceAll", "genderAll", "ageAll", "tagAll");
        dates(fields, "startTime", "endTime", "updatedAt");
        fields.put("title", nonIndexedKeyword());
        fields.put("description", nonIndexedKeyword());
        fields.put("imageUrl", nonIndexedKeyword());
        fields.put("landingPageUrl", nonIndexedKeyword());
        return fields;
    }
    private Map<String,Property> new_candidateProperties(){
        Map<String,Property> map = new LinkedHashMap<>();
        map.put("advertiserPublicId",keyword());
        map.put("rulePublicId",keyword());
        map.put("deviceType",keyword());
        map.put("gender",keyword());
        map.put("ageMin",keyword());
        map.put("ageMax",keyword());
        map.put("regionCode",keyword());
        map.put("planPublicId",keyword());
        map.put("bidPrice",keyword());
        map.put("billingType",keyword());
        map.put("materialPublicId",keyword());
        map.put("slotPublicId",keyword());
        map.put("slotCode",keyword());
        return map;
    }
    private void switchIndex(String newIndex) throws IOException {
        String oldIndex = currentAliasIndex();//得到当前别名对应的物理名称
        List<Action> actions = new ArrayList<>();
        if (oldIndex != null) {
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

    private void switchIndexWithSpringData(String newIndex) throws IOException {
        String alias = properties.getCandidate().getReadAlias();

        String oldIndex = currentAliasIndex();

        AliasActions actions = new AliasActions();
        actions.add(new AliasAction.Remove(
                AliasActionParameters.builder()
                        .withIndices(oldIndex)
                        .withAliases(alias)
                        .build()
        ));
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
    private Property keyword(){
        return new KeywordProperty.Builder().build()._toProperty();
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
