package com.example.adplatform.search.port;

import com.example.adplatform.delivery.request.DeliveryRequest;
import com.example.adplatform.infra.elasticsearch.CandidateDocument;

import java.util.List;

/** 投放候选主数据源查询端口。 */
public interface CandidateSearchPort {

    boolean isEnabled();

    List<CandidateDocument> recall(DeliveryRequest request);
}
