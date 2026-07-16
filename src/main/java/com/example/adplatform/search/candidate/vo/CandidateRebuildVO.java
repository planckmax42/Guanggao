package com.example.adplatform.search.candidate.vo;

import java.time.LocalDateTime;

public record CandidateRebuildVO(
        String indexName,
        int indexedCount,
        int failedCount,
        LocalDateTime switchedAt) {
}
