package com.example.adplatform.search.candidate.dto;

import lombok.Getter;
import lombok.Setter;

import java.time.LocalDateTime;

@Getter
@Setter
public class CandidateSourceRow {
    private Long materialId;
    private Long planId;
    private Long userId;
    private Long slotId;
    private String slotCode;
    private String title;
    private String description;
    private String imageUrl;
    private String landingPageUrl;
    private Integer materialStatus;
    private String auditStatus;
    private String planStatus;
    private Long budgetTotal;
    private Long budgetDaily;
    private Long bidPrice;
    private String billingType;
    private LocalDateTime startTime;
    private LocalDateTime endTime;
    private String region;
    private String deviceType;
    private String gender;
    private Integer ageMin;
    private Integer ageMax;
    private String userTags;
    private LocalDateTime updatedAt;
}
