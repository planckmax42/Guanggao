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

@Getter
@Setter
@NoArgsConstructor
@Document(indexName = "ad-candidate", createIndex = false, writeTypeHint = WriteTypeHint.FALSE)
public class AdCandidateDocument {

    @Id
    private String id;
    private Long materialId;
    private Long planId;
    private Long userId;
    private Long slotId;
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
    @Field(type = FieldType.Date, format = DateFormat.epoch_millis)
    private Instant updatedAt;
}
