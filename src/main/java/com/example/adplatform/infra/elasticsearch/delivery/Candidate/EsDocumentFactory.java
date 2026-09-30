package com.example.adplatform.infra.elasticsearch.delivery.Candidate;

import com.example.adplatform.search.candidate.query.CandidateQueryResult;
import com.example.adplatform.search.candidate.model.CandidateDocument;
import com.example.adplatform.search.port.CandidateDocumentFactory;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;
import org.springframework.util.StringUtils;

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
public class EsDocumentFactory implements CandidateDocumentFactory {//mysql查询结果到ES映射函数

    private static final TypeReference<List<String>> STRING_LIST = new TypeReference<>() { };
    private final ObjectMapper objectMapper;

    /** 根据数据库真实配置生成可直接索引的候选快照。 */
    @Override
    public CandidateDocument from(CandidateQueryResult row) {
        CandidateDocument document = new CandidateDocument();

//        document.setId(row.getMaterialPublicId());
        document.setMaterialPublicId(row.getMaterialPublicId());
        document.setMaterialPublicId(row.getMaterialPublicId());

        document.setPlanPublicId(row.getPlanPublicId());
        document.setBidPrice(row.getBidPrice());
        document.setBillingType(row.getBillingType());

        document.setSlotCode(row.getSlotCode());

        List<String> devices = parseList(row.getDeviceType(), value -> value.toUpperCase(Locale.ROOT));
        return document;
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

}
