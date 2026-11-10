package com.example.adplatform.admin.service.impl;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.example.adplatform.admin.converter.MaterialConverter;
import com.example.adplatform.admin.entity.AdvertiserEntity;
import com.example.adplatform.admin.mapper.AdvertiserMapper;
import com.example.adplatform.admin.request.material.AuditMaterialRequest;
import com.example.adplatform.admin.request.material.CreateMaterialRequest;
import com.example.adplatform.admin.entity.SlotEntity;
import com.example.adplatform.admin.entity.PlanEntity;
import com.example.adplatform.admin.entity.MaterialEntity;
import com.example.adplatform.admin.mapper.SlotMapper;
import com.example.adplatform.admin.mapper.PlanMapper;
import com.example.adplatform.admin.mapper.MaterialMapper;
import com.example.adplatform.admin.port.EventMetadataPort;
import com.example.adplatform.admin.response.material.MaterialOperationResponse;
import com.example.adplatform.admin.service.MaterialService;
import com.example.adplatform.admin.response.material.MaterialQueryResponse;
import com.example.adplatform.common.exception.BusinessException;
import com.example.adplatform.common.exception.ErrorCode;
import com.example.adplatform.admin.response.PageResponse;
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
    private final AdvertiserMapper advertiserMapper;

    @Override
    @Transactional(rollbackFor = Exception.class)
    public MaterialOperationResponse create(CreateMaterialRequest request) {
        if(advertiserMapper.exists(new LambdaQueryWrapper<AdvertiserEntity>()
                .eq(AdvertiserEntity::getPublicId, request.advertiserPublicId())))
            throw new BusinessException(ErrorCode.RESOURCE_NOT_FOUND,"广告主不存在");
        if(slotMapper.exists(new LambdaQueryWrapper<SlotEntity>()
                .eq(SlotEntity::getPublicId, request.slotPublicId())))
            throw new BusinessException(ErrorCode.RESOURCE_NOT_FOUND,"广告位不存在");
        MaterialEntity entity = materialConverter.requestToEntity(request);
        entity.initializePublicId(PublicIdGenerator.generate(PublicIdGenerator.MATERIAL_PREFIX));
        entity.setAdvertiserId(request.advertiserPublicId());
        entity.setSlotId(request.slotPublicId());
        materialMapper.insert(entity);
        return materialConverter.entityToOperationResponse(entity);
    }

    @Override
    public PageResponse<MaterialQueryResponse> pageQuery(
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
        List<MaterialQueryResponse> records = result.getRecords().stream()
                .map(entity -> materialConverter.entityToQueryResponse(
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

}
