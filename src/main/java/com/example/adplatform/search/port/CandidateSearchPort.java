package com.example.adplatform.search.port;

import com.example.adplatform.delivery.request.AdDeliveryRequest;
import com.example.adplatform.search.candidate.model.CandidateDocument;

import java.util.List;

/** 投放候选主数据源查询端口。 */
public interface CandidateSearchPort {

    boolean isEnabled();

    List<CandidateDocument> recall(AdDeliveryRequest request);
}
