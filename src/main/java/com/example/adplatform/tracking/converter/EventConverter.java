package com.example.adplatform.tracking.converter;

import com.example.adplatform.tracking.request.EventRequest;
import com.example.adplatform.tracking.entity.EventEntity;
import com.example.adplatform.tracking.entity.EventType;
import com.example.adplatform.tracking.message.EventMessage;
import com.example.adplatform.tracking.service.EventProcessingContext;
import org.mapstruct.Mapper;
import org.mapstruct.Mapping;

@Mapper(componentModel = "spring")
public interface EventConverter {

    /**
     * 由应用层将 HTTP 请求模型转换为独立的事件消息协议。
     */
    @Mapping(target = "eventType", source = "eventType")
    EventMessage toMessage(EventRequest request, EventType eventType);

    @Mapping(target = "id", ignore = true)
    @Mapping(target = "eventId", source = "message.eventId")
    @Mapping(target = "requestId", source = "message.requestId")
    @Mapping(target = "eventType", expression = "java(message.eventType().name())")
    @Mapping(target = "planId", source = "context.material.planId")
    @Mapping(target = "materialId", source = "message.materialId")
    @Mapping(target = "slotId", source = "context.material.slotId")
    @Mapping(target = "viewerId", source = "message.viewerId")
    @Mapping(target = "billingType", source = "context.billingType")
    @Mapping(target = "eventTime", source = "context.eventTime")
    @Mapping(target = "createdAt", ignore = true)
    EventEntity toEntity(
            EventMessage message,
            EventProcessingContext context);
}
