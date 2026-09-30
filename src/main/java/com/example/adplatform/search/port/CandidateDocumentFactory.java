package com.example.adplatform.search.port;

import com.example.adplatform.search.candidate.model.CandidateDocument;
import com.example.adplatform.search.candidate.query.CandidateQueryResult;

/** 将 MySQL 候选投影转换为统一候选文档的业务边界。 */
public interface CandidateDocumentFactory {

    CandidateDocument from(CandidateQueryResult row);
}
