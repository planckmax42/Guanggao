package com.example.adplatform.tracking.service;

import com.example.adplatform.admin.entity.MaterialEntity;
import com.example.adplatform.admin.entity.PlanEntity;
import com.example.adplatform.tracking.entity.EventType;

import java.time.LocalDate;
import java.time.LocalDateTime;

/** 三条事件消费链路共享的、一次解析完成的业务上下文。 */
public record EventProcessingContext(
        EventType eventType,
        MaterialEntity material,
        PlanEntity plan,
        String billingType,
        LocalDateTime eventTime,
        LocalDate statDate) {
}
