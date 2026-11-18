package com.example.adplatform.delivery.converter;

import com.example.adplatform.delivery.request.DeliveryRequest;
import com.example.adplatform.infra.elasticsearch.QueryCandidateRequest;
import org.mapstruct.Mapper;

@Mapper(componentModel = "spring")
public interface DeliveryConverter {

    QueryCandidateRequest deliveryToCandidateRequest(DeliveryRequest request, Long slotId);
}
