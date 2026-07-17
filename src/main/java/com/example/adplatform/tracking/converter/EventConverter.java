package com.example.adplatform.tracking.converter;

import com.example.adplatform.tracking.request.EventRequest;
import com.example.adplatform.admin.entity.BillingType;
import com.example.adplatform.tracking.entity.EventEntity;
import com.example.adplatform.tracking.entity.EventType;
import com.example.adplatform.tracking.message.EventMessage;
import com.example.adplatform.tracking.service.EventMaterialMetadata;
import org.mapstruct.Mapper;
import org.mapstruct.Mapping;

@Mapper(componentModel = "spring", imports = {BillingType.class, java.time.LocalDateTime.class})
public interface EventConverter {

    /**
     * 由应用层将 HTTP 请求模型转换为独立的事件消息协议。
     */
    @Mapping(target = "eventType", source = "eventType")
    @Mapping(target = "eventTime", expression = "java(request.eventTime() == null ? LocalDateTime.now() : request.eventTime())")
    EventMessage toMessage(EventRequest request, EventType eventType);

    @Mapping(target = "id", ignore = true)
    @Mapping(target = "eventId", source = "message.eventId")
    @Mapping(target = "requestId", source = "message.requestId")
    @Mapping(target = "eventType", expression = "java(message.eventType().name())")
    @Mapping(target = "planId", source = "metadata.planId")
    @Mapping(target = "materialId", source = "message.materialId")
    @Mapping(target = "slotId", source = "metadata.slotId")
    @Mapping(target = "viewerId", source = "message.viewerId")
    @Mapping(target = "billingType", expression = "java(BillingType.normalizeOrDefault(metadata.billingType()))")
    @Mapping(target = "eventTime", source = "message.eventTime")
    @Mapping(target = "createdAt", ignore = true)
    EventEntity toEntity(
            EventMessage message,
            EventMaterialMetadata metadata);
}
