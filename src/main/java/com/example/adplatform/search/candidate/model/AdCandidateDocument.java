package com.example.adplatform.search.candidate.model;

import com.example.adplatform.infra.elasticsearch.delivery.Candidate.LocalDateTimeEpochMillisConverter;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import org.springframework.data.annotation.Id;
import org.springframework.data.elasticsearch.annotations.DateFormat;
import org.springframework.data.elasticsearch.annotations.Document;
import org.springframework.data.elasticsearch.annotations.Field;
import org.springframework.data.elasticsearch.annotations.FieldType;
import org.springframework.data.elasticsearch.annotations.WriteTypeHint;
import org.springframework.data.elasticsearch.annotations.ValueConverter;

import java.time.LocalDateTime;
import java.util.List;

/**
 * ES 中的广告候选快照，一条文档对应一条素材。
 *
 * <p>文档是为在线召回去范式化后的读取模型，不替代 MySQL 业务实体。计划、广告位或
 * 定向规则变化时会重新生成受影响的文档。Java 时间字段使用 LocalDateTime，按应用
 * 默认时区转换为 epoch millis 存入 ES，与现有索引和查询保持一致。</p>
 */
@Getter
@Setter
@NoArgsConstructor
@Document(indexName = "ad-candidate", createIndex = false, writeTypeHint = WriteTypeHint.FALSE)
public class AdCandidateDocument {

    @Id
    private String id;
    private Long materialId;
    private String materialPublicId;
    private String title;
    private String description;
    private String imageUrl;
    private String landingPageUrl;
    private String auditStatus;
    private String materialStatus;

    private Long planId;
    private String planPublicId;
    private Long budgetTotal;
    private Long budgetDaily;
    private Long bidPrice;
    private String billingType;
    @Field(type = FieldType.Date, format = DateFormat.epoch_millis)
    @ValueConverter(LocalDateTimeEpochMillisConverter.class)
    private LocalDateTime startTime;
    @Field(type = FieldType.Date, format = DateFormat.epoch_millis)
    @ValueConverter(LocalDateTimeEpochMillisConverter.class)
    private LocalDateTime endTime;
    private String planStatus;

    private Long slotId;
    private String slotPublicId;
    private String slotCode;

    // “All=true”明确表示该维度不限制；不能仅依赖空数组，因为空 terms 查询不会命中。
    private List<String> regions;
    private boolean regionAll;
    private List<String> deviceTypes;
    private boolean deviceAll;
    private String gender;
    private boolean genderAll;
    private Integer ageMin;
    private Integer ageMax;
    private boolean ageAll;
    private List<String> tags;
    private boolean tagAll;
    // 仅用于排查索引新旧程度，不参与召回排序。
    @Field(type = FieldType.Date, format = DateFormat.epoch_millis)
    @ValueConverter(LocalDateTimeEpochMillisConverter.class)
    private LocalDateTime updatedAt;

    private Long advertiserId;
}
