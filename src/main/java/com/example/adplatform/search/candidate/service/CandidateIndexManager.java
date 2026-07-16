package com.example.adplatform.search.candidate.service;

import com.example.adplatform.common.exception.BusinessException;
import com.example.adplatform.common.exception.ErrorCode;
import com.example.adplatform.search.candidate.dto.CandidateSourceRow;
import com.example.adplatform.search.candidate.mapper.CandidateSourceMapper;
import com.example.adplatform.search.candidate.model.AdCandidateDocument;
import com.example.adplatform.search.candidate.vo.CandidateRebuildVO;
import com.example.adplatform.search.config.AdElasticsearchProperties;
import com.example.adplatform.search.support.ElasticsearchRestSupport;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.elasticsearch.core.ElasticsearchOperations;
import org.springframework.data.elasticsearch.core.mapping.IndexCoordinates;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Service;

import java.io.IOException;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

@Slf4j
@Service
@RequiredArgsConstructor
public class CandidateIndexManager {

    public static final String REBUILDING_KEY = "delivery:es:candidate:rebuilding";
    private static final String REBUILD_LOCK_KEY = "delivery:es:candidate:rebuild-lock";
    private static final DateTimeFormatter INDEX_SUFFIX = DateTimeFormatter.ofPattern("yyyyMMddHHmmssSSS");

    private final AdElasticsearchProperties properties;
    private final ElasticsearchRestSupport restSupport;
    private final ElasticsearchOperations operations;
    private final StringRedisTemplate stringRedisTemplate;
    private final CandidateSourceMapper candidateSourceMapper;
    private final CandidateDocumentFactory documentFactory;

    public boolean aliasExists() {
        if (!properties.isEnabled()) {
            return false;
        }
        try {
            return restSupport.exists("/_alias/" + properties.getCandidate().getReadAlias());
        } catch (Exception ex) {
            return false;
        }
    }

    public CandidateRebuildVO rebuild() {
        if (!properties.isEnabled()) {
            throw new BusinessException(ErrorCode.DEPENDENCY_SERVICE_UNAVAILABLE, "Elasticsearch未启用");
        }
        String lockToken = UUID.randomUUID().toString();
        Boolean locked = stringRedisTemplate.opsForValue().setIfAbsent(
                REBUILD_LOCK_KEY, lockToken, properties.getCandidate().getRebuildLockTtl());
        if (!Boolean.TRUE.equals(locked)) {
            throw new BusinessException(ErrorCode.DUPLICATE_RESOURCE, "候选索引正在重建");
        }
        stringRedisTemplate.opsForValue().set(
                REBUILDING_KEY, lockToken, properties.getCandidate().getRebuildLockTtl());
        String indexName = "ad-candidate-" + LocalDateTime.now().format(INDEX_SUFFIX);
        int indexed = 0;
        try {
            createIndex(indexName);
            List<CandidateSourceRow> rows = candidateSourceMapper.selectAllEligible();
            for (int from = 0; from < rows.size(); from += 500) {
                int to = Math.min(from + 500, rows.size());
                List<AdCandidateDocument> documents = rows.subList(from, to).stream()
                        .map(documentFactory::from)
                        .toList();
                operations.save(documents, IndexCoordinates.of(indexName));
                indexed += documents.size();
            }
            operations.indexOps(IndexCoordinates.of(indexName)).refresh();
            switchAliases(indexName);
            return new CandidateRebuildVO(indexName, indexed, 0, LocalDateTime.now());
        } catch (Exception ex) {
            log.error("Candidate index rebuild failed, targetIndex={}", indexName, ex);
            throw new BusinessException(ErrorCode.DEPENDENCY_SERVICE_UNAVAILABLE, "候选索引重建失败");
        } finally {
            releaseLock(lockToken);
        }
    }

    private void createIndex(String indexName) throws IOException {
        Map<String, Object> mapping = new LinkedHashMap<>();
        mapping.put("dynamic", "strict");
        mapping.put("properties", candidateProperties());
        restSupport.put("/" + indexName, Map.of(
                "settings", Map.of("number_of_shards", 1, "number_of_replicas", 0, "refresh_interval", "1s"),
                "mappings", mapping));
    }

    private Map<String, Object> candidateProperties() {
        Map<String, Object> fields = new LinkedHashMap<>();
        keyword(fields, "id", "slotCode", "materialStatus", "auditStatus", "planStatus", "billingType", "gender");
        keyword(fields, "regions", "deviceTypes", "tags");
        longs(fields, "materialId", "planId", "userId", "slotId", "budgetTotal", "budgetDaily", "bidPrice");
        integers(fields, "ageMin", "ageMax");
        booleans(fields, "regionAll", "deviceAll", "genderAll", "ageAll", "tagAll");
        dates(fields, "startTime", "endTime", "updatedAt");
        fields.put("title", Map.of("type", "keyword", "index", false));
        fields.put("description", Map.of("type", "keyword", "index", false));
        fields.put("imageUrl", Map.of("type", "keyword", "index", false));
        fields.put("landingPageUrl", Map.of("type", "keyword", "index", false));
        return fields;
    }

    private void switchAliases(String newIndex) throws IOException {
        Set<String> oldIndices = currentAliasIndices();
        List<Map<String, Object>> actions = new ArrayList<>();
        for (String oldIndex : oldIndices) {
            actions.add(Map.of("remove", Map.of(
                    "index", oldIndex,
                    "aliases", List.of(properties.getCandidate().getReadAlias(), properties.getCandidate().getWriteAlias()),
                    "must_exist", false)));
        }
        actions.add(Map.of("add", Map.of("index", newIndex, "alias", properties.getCandidate().getReadAlias())));
        actions.add(Map.of("add", Map.of("index", newIndex, "alias", properties.getCandidate().getWriteAlias(), "is_write_index", true)));
        restSupport.post("/_aliases", Map.of("actions", actions));
    }

    private Set<String> currentAliasIndices() {
        try {
            return restSupport.get("/_alias/" + properties.getCandidate().getReadAlias()).keySet();
        } catch (Exception ex) {
            return Set.of();
        }
    }

    private void releaseLock(String token) {
        try {
            if (token.equals(stringRedisTemplate.opsForValue().get(REBUILD_LOCK_KEY))) {
                stringRedisTemplate.delete(List.of(REBUILD_LOCK_KEY, REBUILDING_KEY));
            }
        } catch (RuntimeException ex) {
            log.warn("Failed to release candidate rebuild lock", ex);
        }
    }

    private void keyword(Map<String, Object> fields, String... names) {
        for (String name : names) fields.put(name, Map.of("type", "keyword"));
    }
    private void longs(Map<String, Object> fields, String... names) {
        for (String name : names) fields.put(name, Map.of("type", "long"));
    }
    private void integers(Map<String, Object> fields, String... names) {
        for (String name : names) fields.put(name, Map.of("type", "integer"));
    }
    private void booleans(Map<String, Object> fields, String... names) {
        for (String name : names) fields.put(name, Map.of("type", "boolean"));
    }
    private void dates(Map<String, Object> fields, String... names) {
        for (String name : names) fields.put(name, Map.of("type", "date", "format", "strict_date_optional_time||epoch_millis"));
    }
}
