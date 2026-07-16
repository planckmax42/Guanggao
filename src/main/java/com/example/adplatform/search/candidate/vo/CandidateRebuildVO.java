package com.example.adplatform.search.candidate.vo;

import java.time.LocalDateTime;

/** 候选索引全量重建结果。 */
public record CandidateRebuildVO(
        String indexName,
        int indexedCount,
        int failedCount,
        LocalDateTime switchedAt) {
}
