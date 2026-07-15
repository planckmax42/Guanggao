package com.example.adplatform.tracking.converter;

import com.example.adplatform.admin.entity.MaterialEntity;
import com.example.adplatform.tracking.dto.EventRequest;
import com.example.adplatform.tracking.entity.EventEntity;
import com.example.adplatform.tracking.entity.EventType;
import com.example.adplatform.tracking.message.EventMessage;
import org.mapstruct.Mapper;
import org.mapstruct.Mapping;

import java.time.LocalDateTime;

@Mapper(componentModel = "spring")
public interface EventConverter {

    /**
     * 由应用层将 HTTP 请求模型转换为独立的事件消息协议。
     */
    EventMessage toMessage(EventRequest request);

    @Mapping(target = "id", ignore = true)
    @Mapping(target = "eventId", source = "message.eventId")
    @Mapping(target = "requestId", source = "message.requestId")
    @Mapping(target = "eventType", expression = "java(eventType.name())")
    @Mapping(target = "planId", source = "material.planId")
    @Mapping(target = "materialId", source = "message.materialId")
    @Mapping(target = "slotId", source = "material.slotId")
    @Mapping(target = "viewerId", source = "message.viewerId")
    @Mapping(target = "billingType", source = "billingType")
    @Mapping(target = "charged", expression = "java(charged ? 1 : 0)")
    @Mapping(target = "costAmount", source = "costAmount")
    @Mapping(target = "eventTime", source = "eventTime")
    @Mapping(target = "createdAt", ignore = true)
    EventEntity toEntity(
            EventMessage message,
            EventType eventType,
            MaterialEntity material,
            String billingType,
            boolean charged,
            long costAmount,
            LocalDateTime eventTime);
}
