package com.example.adplatform.admin.service.impl;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.example.adplatform.admin.converter.MaterialConverter;
import com.example.adplatform.admin.request.AuditMaterialRequest;
import com.example.adplatform.admin.request.CreateMaterialRequest;
import com.example.adplatform.admin.entity.SlotEntity;
import com.example.adplatform.admin.entity.PlanEntity;
import com.example.adplatform.admin.entity.MaterialEntity;
import com.example.adplatform.admin.mapper.SlotMapper;
import com.example.adplatform.admin.mapper.PlanMapper;
import com.example.adplatform.admin.mapper.MaterialMapper;
import com.example.adplatform.admin.port.EventMetadataPort;
import com.example.adplatform.admin.service.MaterialService;
import com.example.adplatform.admin.response.MaterialResponse;
import com.example.adplatform.common.exception.BusinessException;
import com.example.adplatform.common.exception.ErrorCode;
import com.example.adplatform.common.enums.CommonStatus;
import com.example.adplatform.common.response.PageResponse;
import com.example.adplatform.common.response.ResourceRefResponse;
import com.example.adplatform.common.id.PublicIdGenerator;
import com.example.adplatform.search.candidate.event.ConfigStopGuardEvent;
import com.example.adplatform.search.outbox.message.ConfigAggregateType;
import com.example.adplatform.search.outbox.service.SearchOutboxService;
import com.example.adplatform.tracking.service.EventMaterialMetadata;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;

import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

/**
 * 素材管理服务。
 *
 * <p>素材数据与配置 Outbox 在同一 MySQL 事务提交；审核拒绝时再发布事务后本地事件，
 * 立即写入 Redis 停投集合，覆盖 Kafka 尚未同步 ES 的短暂窗口。</p>
 */
@RequiredArgsConstructor
@Service
public class MaterialServiceImpl implements MaterialService {

    private final MaterialMapper materialMapper;
    private final PlanMapper planMapper;
    private final SlotMapper slotMapper;
    private final MaterialConverter materialConverter;
    private final SearchOutboxService searchOutboxService;
    private final ApplicationEventPublisher applicationEventPublisher;
    private final EventMetadataPort eventMetadataCacheService;

    @Override
    @Transactional(rollbackFor = Exception.class)
    public ResourceRefResponse create(CreateMaterialRequest request) {
        PlanEntity plan = planMapper.selectOne(new LambdaQueryWrapper<PlanEntity>()
                .eq(PlanEntity::getPublicId, request.planPublicId()));
        if (plan == null) {
            throw new BusinessException(ErrorCode.RESOURCE_NOT_FOUND, "广告计划不存在");
        }
        SlotEntity slot = slotMapper.selectOne(new LambdaQueryWrapper<SlotEntity>()
                .eq(SlotEntity::getPublicId, request.slotPublicId()));
        if (slot == null) {
            throw new BusinessException(ErrorCode.RESOURCE_NOT_FOUND, "广告位不存在");
        }
        if (slot.getStatus() == null || slot.getStatus() != CommonStatus.ENABLED) {
            throw new BusinessException(ErrorCode.BUSINESS_ERROR, "广告位已停用，不能创建素材");
        }

        MaterialEntity entity = materialConverter.toEntity(request);
        entity.initializePublicId(PublicIdGenerator.generate(PublicIdGenerator.MATERIAL_PREFIX));
        entity.setPlanId(plan.getId());
        entity.setSlotId(slot.getId());
        materialMapper.insert(entity);
        eventMetadataCacheService.refreshAfterCommit(entity.getPublicId(), new EventMaterialMetadata(
                entity.getId(),
                plan.getId(),
                entity.getSlotId(),
                plan.getBudgetTotal(),
                plan.getBudgetDaily(),
                plan.getBidPrice(),
                plan.getBillingType()));
        searchOutboxService.appendConfigChange(ConfigAggregateType.MATERIAL, entity.getPublicId());
        return materialConverter.toRef(entity);
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public MaterialResponse audit(String publicId, AuditMaterialRequest request) {
        MaterialEntity entity = materialMapper.selectOne(new LambdaQueryWrapper<MaterialEntity>()
                .eq(MaterialEntity::getPublicId, publicId));
        if (entity == null) {
            throw new BusinessException(ErrorCode.RESOURCE_NOT_FOUND, "广告素材不存在");
        }
        entity.setAuditStatus(request.auditStatus());
        materialMapper.updateById(entity);
        searchOutboxService.appendConfigChange(ConfigAggregateType.MATERIAL, publicId);
        applicationEventPublisher.publishEvent(new ConfigStopGuardEvent(
                ConfigAggregateType.MATERIAL,
                entity.getId(),
                !"APPROVED".equals(request.auditStatus())));
        return toResponse(materialMapper.selectById(entity.getId()));
    }

    @Override
    public PageResponse<MaterialResponse> pageQuery(
            long current,
            long size,
            String planPublicId,
            String auditStatus) {
        Long planId = planPublicId == null ? null : getPlanByPublicId(planPublicId).getId();
        Page<MaterialEntity> page = new Page<>(current, size);
        LambdaQueryWrapper<MaterialEntity> query = new LambdaQueryWrapper<MaterialEntity>()
                .eq(planId != null, MaterialEntity::getPlanId, planId)
                .eq(StringUtils.hasText(auditStatus), MaterialEntity::getAuditStatus, auditStatus)
                .orderByDesc(MaterialEntity::getId);
        Page<MaterialEntity> result = materialMapper.selectPage(page, query);
        List<Long> planIds = result.getRecords().stream()
                .map(MaterialEntity::getPlanId)
                .distinct()
                .toList();
        Map<Long, String> planPublicIds = planIds.isEmpty()
                ? Map.of()
                : planMapper.selectBatchIds(planIds).stream()
                        .collect(Collectors.toMap(PlanEntity::getId, PlanEntity::getPublicId));
        List<Long> slotIds = result.getRecords().stream()
                .map(MaterialEntity::getSlotId)
                .distinct()
                .toList();
        Map<Long, String> slotPublicIds = slotIds.isEmpty()
                ? Map.of()
                : slotMapper.selectBatchIds(slotIds).stream()
                        .collect(Collectors.toMap(SlotEntity::getId, SlotEntity::getPublicId));
        List<MaterialResponse> records = result.getRecords().stream()
                .map(entity -> materialConverter.toResponse(
                        entity,
                        planPublicIds.get(entity.getPlanId()),
                        slotPublicIds.get(entity.getSlotId())))
                .toList();
        return PageResponse.of(result, records);
    }

    private PlanEntity getPlanByPublicId(String publicId) {
        PlanEntity plan = planMapper.selectOne(new LambdaQueryWrapper<PlanEntity>()
                .eq(PlanEntity::getPublicId, publicId));
        if (plan == null) {
            throw new BusinessException(ErrorCode.RESOURCE_NOT_FOUND, "广告计划不存在");
        }
        return plan;
    }

    private MaterialResponse toResponse(MaterialEntity entity) {
        PlanEntity plan = planMapper.selectById(entity.getPlanId());
        SlotEntity slot = slotMapper.selectById(entity.getSlotId());
        return materialConverter.toResponse(
                entity,
                plan == null ? null : plan.getPublicId(),
                slot == null ? null : slot.getPublicId());
    }
}
