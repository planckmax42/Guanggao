package com.example.adplatform.search.candidate.query;

import lombok.Getter;
import lombok.Setter;

/**
 * MySQL 候选联表查询结果，不作为外部接口 DTO。
 *
 * <p>该对象保留数据库原始格式，随后由 EsDocumentFactory 完成枚举、JSON 定向、
 * 大小写和时间类型规范化。</p>
 */
@Getter
@Setter
public class CandidateQueryResult {

    private String advertiserPublicId;

    private String rulePublicId;
    private String deviceType;
    private String gender;
    private Integer ageMin;
    private Integer ageMax;

    private String regionCode;

    private String planPublicId;
    private Long bidPrice;
    private String billingType;

    private String materialPublicId;

    private String slotPublicId;
    private String slotCode;

}
