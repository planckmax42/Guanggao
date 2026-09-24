package com.example.adplatform.search.candidate.query;

import lombok.Getter;
import lombok.Setter;

import java.time.LocalDateTime;

/**
 * MySQL 候选联表查询结果，不作为外部接口 DTO。
 *
 * <p>该对象保留数据库原始格式，随后由 EsDocumentFactory 完成枚举、JSON 定向、
 * 大小写和时间类型规范化。</p>
 */
@Getter
@Setter
public class CandidateSourceRow {

    private Long materialId;
    private String materialPublicId;
    private String title;
    private String description;
    private String imageUrl;
    private String landingPageUrl;
    private String auditStatus;
    private Integer materialStatus;

    private Long planId;
    private String planPublicId;
    private Long budgetTotal;
    private Long budgetDaily;
    private Long bidPrice;
    private String billingType;
    private LocalDateTime startTime;
    private LocalDateTime endTime;
    private String planStatus;

    private Long slotId;
    private String slotPublicId;
    private String slotCode;

    private String region;
    private String deviceType;
    private String gender;
    private Integer ageMin;
    private Integer ageMax;
    private String userTags;
    private LocalDateTime updatedAt;

    private Long advertiserId;
}
