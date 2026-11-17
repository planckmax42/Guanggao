package com.example.adplatform.search.candidate.query;

import lombok.Getter;
import lombok.Setter;
import java.util.List;

@Getter
@Setter
public class CandidateQueryResult {

    private Long materialId;
    private Long slotId;
    private List<String> deviceTypes;
    private String gender;
    private Integer ageMin;
    private Integer ageMax;
    private List<String> regions;
    private Long budget_total;
    private Long budget_daily;
    private Long bidPrice;
    private String billingType;
}
