package com.example.adplatform.tracking.converter;

import com.example.adplatform.admin.entity.CreativeEntity;
import com.example.adplatform.tracking.dto.AdEventRequest;
import com.example.adplatform.tracking.entity.AdEventEntity;
import com.example.adplatform.tracking.entity.AdEventType;
import org.mapstruct.Mapper;
import org.mapstruct.Mapping;

import java.time.LocalDateTime;

@Mapper(componentModel = "spring")
public interface AdEventConverter {

    @Mapping(target = "id", ignore = true)
    @Mapping(target = "eventType", expression = "java(eventType.name())")
    @Mapping(target = "campaignId", source = "request.campaignId")
    @Mapping(target = "creativeId", source = "request.creativeId")
    @Mapping(target = "adSlotId", source = "creative.adSlotId")
    @Mapping(target = "userId", source = "request.userId")
    @Mapping(target = "charged", expression = "java(charged ? 1 : 0)")
    @Mapping(target = "createdAt", ignore = true)
    AdEventEntity toEntity(
            AdEventRequest request,
            AdEventType eventType,
            CreativeEntity creative,
            String billingType,
            boolean charged,
            long costAmount,
            LocalDateTime eventTime);
}
