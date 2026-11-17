package com.example.adplatform.infra.elasticsearch;

/** 候选查询结果，仅包含后续投放所需的标识和出价信息。 */
public record QueryCandidateResponse(
        Long materialId,
        Long slotId,
        Long planId,
        Long bidPrice,
        String billingType) {
}
