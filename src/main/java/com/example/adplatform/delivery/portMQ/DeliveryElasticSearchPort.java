package com.example.adplatform.delivery.portMQ;

import com.example.adplatform.infra.elasticsearch.QueryCandidateResponse;
import com.example.adplatform.infra.elasticsearch.CreateCandidateRequest;
import com.example.adplatform.infra.elasticsearch.QueryCandidateRequest;
import org.springframework.data.domain.Range;
import org.springframework.data.elasticsearch.core.query.ByQueryResponse;
import java.util.List;

public interface DeliveryElasticSearchPort {

    String create(CreateCandidateRequest request);

    List<QueryCandidateResponse> query(QueryCandidateRequest request);

    ByQueryResponse updateSlotId(Long materialId, String field, Long oldValue, Long newValue);

    ByQueryResponse updateBudgetTotal(Long planId, String field, Long oldValue, Long newValue);

    ByQueryResponse updateBudgetDaily(Long planId, String field, Long oldValue, Long newValue);

    ByQueryResponse updateBidPrice(Long planId, String field, Long oldValue, Long newValue);

    ByQueryResponse updateBillingType(Long planId, String field, String oldValue, String newValue);

    ByQueryResponse updateDeviceTypes(Long ruleId, String field, List<String> oldValue, List<String> newValue);

    ByQueryResponse updateGender(Long ruleId, String field, String oldValue, String newValue);

    ByQueryResponse updateAgeRange(Long ruleId, String field, Range<Integer> oldValue, Range<Integer> newValue);

    ByQueryResponse updateRegions(Long ruleId, String field, List<String> oldValue, List<String> newValue);

    ByQueryResponse deleteByPlanId(Long planId);

    ByQueryResponse deleteByMaterialId(Long materialId);

    ByQueryResponse deleteBySlotId(Long slotId);
}
