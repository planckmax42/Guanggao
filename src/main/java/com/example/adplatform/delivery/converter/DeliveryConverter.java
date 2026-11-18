package com.example.adplatform.delivery.converter;

import com.example.adplatform.delivery.request.DeliveryRequest;
import com.example.adplatform.delivery.response.AdDeliveryResponse;
import com.example.adplatform.infra.elasticsearch.QueryCandidateRequest;
import com.example.adplatform.infra.elasticsearch.QueryCandidateResponse;
import com.example.adplatform.infra.redis.delivery.PlanPrice.PlanPrice;
import com.example.adplatform.infra.redis.delivery.materialMetadata.MaterialMetadata;
import org.mapstruct.Mapper;
import org.mapstruct.Mapping;

import java.util.Optional;

@Mapper(componentModel = "spring")
public interface DeliveryConverter {

    @Mapping(target = "billingType", source = "request.billiType")
    QueryCandidateRequest deliveryToCandidateRequest(DeliveryRequest request, Long slotId);

    PlanPrice  queryCandidateTOPlanPrice(Optional<QueryCandidateResponse> queryCandidateResponse);

    AdDeliveryResponse toResponse(MaterialMetadata materialMetadata,String deliveryId);
}
