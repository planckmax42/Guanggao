package com.example.adplatform.search.candidate.model;

import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import org.springframework.data.annotation.Id;
import org.springframework.data.elasticsearch.annotations.DateFormat;
import org.springframework.data.elasticsearch.annotations.Document;
import org.springframework.data.elasticsearch.annotations.Field;
import org.springframework.data.elasticsearch.annotations.FieldType;
import org.springframework.data.elasticsearch.annotations.WriteTypeHint;

import java.time.Instant;
import java.util.List;

/**
 * ES 中的广告候选快照，一条文档对应一条素材。
 *
 * <p>文档是为在线召回去范式化后的读取模型，不替代 MySQL 业务实体。计划、广告位或
 * 定向规则变化时会重新生成受影响的文档。时间统一存为 epoch millis，避免 JVM 与 ES
 * 时区解释不一致。</p>
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
    private Long planId;
    private String planPublicId;
    private Long userId;
    private Long slotId;
    private String slotPublicId;
    private String slotCode;
    private String title;
    private String description;
    private String imageUrl;
    private String landingPageUrl;
    private String materialStatus;
    private String auditStatus;
    private String planStatus;
    private Long budgetTotal;
    private Long budgetDaily;
    private Long bidPrice;
    private String billingType;
    @Field(type = FieldType.Date, format = DateFormat.epoch_millis)
    private Instant startTime;
    @Field(type = FieldType.Date, format = DateFormat.epoch_millis)
    private Instant endTime;

    // “All=true”明确表示该维度不限制；不能仅依赖空数组，因为空 terms 查询不会命中。
    private boolean regionAll;
    private List<String> regions;
    private boolean deviceAll;
    private List<String> deviceTypes;
    private boolean genderAll;
    private String gender;
    private boolean ageAll;
    private Integer ageMin;
    private Integer ageMax;
    private boolean tagAll;
    private List<String> tags;

    // 仅用于排查索引新旧程度，不参与召回排序。
    @Field(type = FieldType.Date, format = DateFormat.epoch_millis)
    private Instant updatedAt;
}
