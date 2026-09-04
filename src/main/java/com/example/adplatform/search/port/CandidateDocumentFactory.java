package com.example.adplatform.search.port;

import com.example.adplatform.search.candidate.model.AdCandidateDocument;
import com.example.adplatform.search.candidate.query.CandidateSourceRow;

/** 将 MySQL 候选投影转换为统一候选文档的业务边界。 */
public interface CandidateDocumentFactory {

    AdCandidateDocument from(CandidateSourceRow row);
}
