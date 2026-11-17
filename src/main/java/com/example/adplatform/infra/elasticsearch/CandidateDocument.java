package com.example.adplatform.infra.elasticsearch;

import lombok.Data;
import org.springframework.data.annotation.Id;
import org.springframework.data.domain.Range;
import org.springframework.data.elasticsearch.annotations.*;
import java.util.List;

@Data
@Setting(shards = 2)
@Document(
        indexName = "ad-candidate",
        createIndex = false,
        writeTypeHint = WriteTypeHint.FALSE,
        dynamic = Dynamic.STRICT
)
public class CandidateDocument {
    @Id
    private String id;
    @Field(type = FieldType.Keyword)
    private Long materialId;
    @Field(type = FieldType.Keyword)
    private Long slotId;
    @Field(type = FieldType.Keyword)
    private Long planId;
    @Field(type = FieldType.Long)
    private Long bidPrice;
    @Field(type = FieldType.Keyword)
    private String billingType;
    @Field(type = FieldType.Keyword)
    private Long ruleId;
    @Field(type = FieldType.Keyword)
    private List<String> deviceTypes;
    @Field(type = FieldType.Keyword)
    private String gender;
    @Field(type = FieldType.Integer_Range)
    private Range<Integer> ageRange;
    @Field(type = FieldType.Keyword)
    private List<String> regions;


}
