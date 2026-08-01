package com.example.adplatform.search.port;

import com.example.adplatform.search.candidate.response.CandidateRebuildResponse;

/** 候选索引人工管理端口。 */
public interface CandidateIndexAdministrationPort {

    CandidateRebuildResponse rebuild();
}
