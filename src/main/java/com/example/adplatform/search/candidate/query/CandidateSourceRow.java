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

    private String materialPublicId;
    private String materialAuditStatus;
    private Integer materialStatus;

    private String planPublicId;
    private Long bidPrice;
    private String billingType;
    private LocalDateTime startTime;
    private LocalDateTime endTime;
    private String planStatus;

    private String slotCode;

    private String rulePublicId;
    private String region;
    private String deviceType;
    private String gender;
    private Integer ageMin;
    private Integer ageMax;

}
