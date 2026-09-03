package com.example.adplatform.infra.elasticsearch.delivery.Candidate;

import com.example.adplatform.common.enums.CommonStatus;
import com.example.adplatform.search.candidate.query.CandidateSourceRow;
import com.example.adplatform.search.candidate.model.AdCandidateDocument;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;
import org.springframework.util.StringUtils;

import java.time.Instant;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.util.List;
import java.util.Locale;
import java.util.function.UnaryOperator;

/**
 * 将 MySQL 联表投影转换为扁平化 ES 候选文档。
 *
 * <p>在写索引前统一大小写和时间类型，避免每次查询重复做规范化。定向 JSON 为空时会
 * 写入对应的 {@code *All=true}，明确表达“该维度不限制”，而不是依赖字段缺失语义。</p>
 */
@Component
@RequiredArgsConstructor
public class EsDocumentFactory {//mysql查询结果到ES映射函数

    private static final TypeReference<List<String>> STRING_LIST = new TypeReference<>() { };
    private final ObjectMapper objectMapper;

    /** 根据数据库真实配置生成可直接索引的候选快照。 */
    public AdCandidateDocument from(CandidateSourceRow row) {
        AdCandidateDocument document = new AdCandidateDocument();
        document.setId(row.getMaterialPublicId());
        document.setMaterialId(row.getMaterialId());
        document.setMaterialPublicId(row.getMaterialPublicId());
        document.setPlanId(row.getPlanId());
        document.setPlanPublicId(row.getPlanPublicId());
        document.setUserId(row.getUserId());
        document.setSlotId(row.getSlotId());
        document.setSlotPublicId(row.getSlotPublicId());
        document.setSlotCode(normalize(row.getSlotCode(), String::toUpperCase));
        document.setTitle(row.getTitle());
        document.setDescription(row.getDescription());
        document.setImageUrl(row.getImageUrl());
        document.setLandingPageUrl(row.getLandingPageUrl());
        document.setMaterialStatus(CommonStatus.ENABLED == row.getMaterialStatus() ? "ENABLED" : "DISABLED");
        document.setAuditStatus(row.getAuditStatus());
        document.setPlanStatus(row.getPlanStatus());
        document.setBudgetTotal(row.getBudgetTotal());
        document.setBudgetDaily(row.getBudgetDaily());
        document.setBidPrice(row.getBidPrice());
        document.setBillingType(row.getBillingType());
        document.setStartTime(toInstant(row.getStartTime()));
        document.setEndTime(toInstant(row.getEndTime()));

        List<String> regions = parseList(row.getRegion(), value -> value.toUpperCase(Locale.ROOT));
        document.setRegionAll(regions.isEmpty());
        document.setRegions(regions);
        List<String> devices = parseList(row.getDeviceType(), value -> value.toUpperCase(Locale.ROOT));
        document.setDeviceAll(devices.isEmpty());
        document.setDeviceTypes(devices);
        document.setGenderAll(!StringUtils.hasText(row.getGender()));
        document.setGender(normalize(row.getGender(), value -> value.toUpperCase(Locale.ROOT)));
        document.setAgeAll(row.getAgeMin() == null && row.getAgeMax() == null);
        document.setAgeMin(row.getAgeMin() == null ? 0 : row.getAgeMin());
        document.setAgeMax(row.getAgeMax() == null ? 120 : row.getAgeMax());
        List<String> tags = parseList(row.getUserTags(), value -> value.toLowerCase(Locale.ROOT));
        document.setTagAll(tags.isEmpty());
        document.setTags(tags);
        document.setUpdatedAt(toInstant(row.getUpdatedAt()));
        return document;
    }

    private Instant toInstant(LocalDateTime value) {
        return value == null ? null : value.atZone(ZoneId.systemDefault()).toInstant();
    }

    private List<String> parseList(String json, UnaryOperator<String> normalizer) {
        if (!StringUtils.hasText(json)) {
            return List.of();
        }
        try {
            return objectMapper.readValue(json, STRING_LIST).stream()
                    .filter(StringUtils::hasText)
                    .map(normalizer)
                    .distinct()
                    .toList();
        } catch (Exception ex) {
            // 定向配置格式错误不能静默放宽为“全部”，否则可能造成越权投放。
            throw new IllegalArgumentException("Invalid targeting rule JSON", ex);
        }
    }

    private String normalize(String value, UnaryOperator<String> normalizer) {
        return StringUtils.hasText(value) ? normalizer.apply(value) : null;
    }
}
