package com.example.adplatform.infra.elasticsearch;

import java.util.List;

/** 创建候选文档的业务数据，文档 ID 由 ES 自动生成。 */
public record CreateCandidateRequest(
        Long materialId,
        Long slotId,
        Long planId,
        Long bidPrice,
        String billingType,
        List<String> deviceTypes,
        String gender,
        Integer ageMin,
        Integer ageMax,
        List<String> regions) {
}
