package com.example.adplatform.tracking.service;

import com.example.adplatform.admin.entity.BillingType;
import com.example.adplatform.admin.entity.MaterialEntity;
import com.example.adplatform.admin.entity.PlanEntity;
import com.example.adplatform.admin.mapper.MaterialMapper;
import com.example.adplatform.admin.query.MaterialPlanJoinRow;
import com.example.adplatform.common.exception.BusinessException;
import com.example.adplatform.common.exception.ErrorCode;
import com.example.adplatform.tracking.entity.EventType;
import com.example.adplatform.tracking.message.EventMessage;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

import java.time.LocalDateTime;

/** 解析事件类型，并读取归档、计费和统计共同需要的素材计划信息。 */
@RequiredArgsConstructor
@Service
public class EventContextResolver {

    private final MaterialMapper materialMapper;

    public EventProcessingContext resolve(EventMessage message) {
        EventType eventType = EventType.parse(message.eventType());
        MaterialPlanJoinRow row = materialMapper.selectMaterialPlanById(message.materialId());
        if (row == null) {
            throw new BusinessException(ErrorCode.RESOURCE_NOT_FOUND, "广告素材不存在");
        }
        if (row.getPlanId() == null) {
            throw new BusinessException(ErrorCode.RESOURCE_NOT_FOUND, "广告计划不存在");
        }

        MaterialEntity material = new MaterialEntity();
        material.setId(row.getMaterialId());
        material.setPlanId(row.getMaterialPlanId());
        material.setSlotId(row.getSlotId());

        PlanEntity plan = new PlanEntity();
        plan.setId(row.getPlanId());
        plan.setBudgetTotal(row.getBudgetTotal());
        plan.setBudgetDaily(row.getBudgetDaily());
        plan.setBidPrice(row.getBidPrice());
        plan.setBillingType(row.getBillingType());

        LocalDateTime eventTime = message.eventTime() == null ? LocalDateTime.now() : message.eventTime();
        return new EventProcessingContext(
                eventType,
                material,
                plan,
                BillingType.normalizeOrDefault(plan.getBillingType()),
                eventTime,
                eventTime.toLocalDate());
    }
}
