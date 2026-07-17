package com.example.adplatform.admin.query;

import lombok.Getter;
import lombok.Setter;

import java.time.LocalDateTime;

/**
 * 广告召回阶段批量查询计划和定向规则的 JOIN 结果。
 */
@Getter
@Setter
public class PlanRuleJoinRow {

    private Long planId;
    private Long userId;
    private String planName;
    private Long budgetTotal;
    private Long budgetDaily;
    private Long bidPrice;
    private String billingType;
    private LocalDateTime startTime;
    private LocalDateTime endTime;
    private String planStatus;
    private LocalDateTime planCreatedAt;
    private LocalDateTime planUpdatedAt;

    private Long ruleId;
    private String region;
    private String deviceType;
    private String gender;
    private Integer ageMin;
    private Integer ageMax;
    private String userTags;
    private LocalDateTime ruleCreatedAt;
    private LocalDateTime ruleUpdatedAt;
}
