package com.example.adplatform.delivery.converter;

import com.example.adplatform.delivery.request.DeliveryRequest;
import com.example.adplatform.infra.elasticsearch.QueryCandidateRequest;
import org.mapstruct.Mapper;
import org.mapstruct.Mapping;

@Mapper(componentModel = "spring")
public interface DeliveryConverter {

    @Mapping(target = "billingType", source = "request.billiType")
    QueryCandidateRequest deliveryToCandidateRequest(DeliveryRequest request, Long slotId);
}
