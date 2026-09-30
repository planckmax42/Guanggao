package com.example.adplatform.search.candidate.model;

import lombok.Data;
import org.springframework.data.annotation.Id;
import org.springframework.data.domain.Range;
import org.springframework.data.elasticsearch.annotations.*;

import java.util.List;

/**
 * ES 中的广告候选快照，一条文档对应一条素材。
 *
 * <p>文档是为在线召回去范式化后的读取模型，不替代 MySQL 业务实体。计划、广告位或
 * 定向规则变化时会重新生成受影响的文档。Java 时间字段使用 LocalDateTime，按应用
 * 默认时区转换为 epoch millis 存入 ES，与现有索引和查询保持一致。</p>
 */
@Data
@Setting(shards = 2)
@Document(
        indexName = "AdPlatformCandidate",
        createIndex = false,
        writeTypeHint = WriteTypeHint.FALSE,
        dynamic = Dynamic.STRICT
)
public class CandidateDocument {

    @Id
    private String id;
    @Field(type = FieldType.Keyword)
    private String advertiserPublicId;
    @Field(type = FieldType.Keyword)
    private String rulePublicId;
    @Field(type = FieldType.Keyword)
    private List<String> deviceType;
    @Field(type = FieldType.Keyword)
    private String gender;
    @Field(type = FieldType.Integer_Range)
    private Range<Integer> ageRange;
    @Field(type = FieldType.Keyword)
    private List<String> regionCode;
    @Field(type = FieldType.Keyword)
    private String planPublicId;
    @Field(type = FieldType.Long)
    private Long bidPrice;
    @Field(type = FieldType.Keyword)
    private String billingType;
    @Field(type = FieldType.Keyword)
    private String materialPublicId;
    @Field(type = FieldType.Keyword)
    private String slotPublicId;
    @Field(type = FieldType.Keyword)
    private String slotCode;


}
