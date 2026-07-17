package com.example.adplatform.search.candidate.response;

import java.time.LocalDateTime;

/** 候选索引全量重建结果。 */
public record CandidateRebuildResponse(
        String indexName,
        int indexedCount,
        int failedCount,
        LocalDateTime switchedAt) {
}
