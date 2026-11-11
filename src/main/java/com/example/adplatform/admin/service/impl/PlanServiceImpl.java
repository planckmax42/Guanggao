package com.example.adplatform.admin.service.impl;

import com.example.adplatform.admin.request.plan.DeletePlanRequest;
import com.example.adplatform.admin.request.plan.PagePlanRequest;
import com.example.adplatform.admin.request.plan.UpdatePlanStatusRequest;
import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.conditions.update.LambdaUpdateWrapper;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.example.adplatform.admin.converter.PlanConverter;
import com.example.adplatform.admin.request.plan.CreatePlanRequest;
import com.example.adplatform.admin.request.plan.UpdatePlanRequest;
import com.example.adplatform.admin.entity.AdvertiserEntity;
import com.example.adplatform.admin.entity.PlanEntity;
import com.example.adplatform.admin.entity.PlanStatus;
import com.example.adplatform.admin.mapper.AdvertiserMapper;
import com.example.adplatform.admin.mapper.PlanMapper;
import com.example.adplatform.admin.port.EventMetadataPort;
import com.example.adplatform.admin.response.plan.PlanOperationResponse;
import com.example.adplatform.admin.service.PlanService;
import com.example.adplatform.admin.response.plan.PlanResponse;
import com.example.adplatform.common.enums.CommonStatus;
import com.example.adplatform.common.exception.BusinessException;
import com.example.adplatform.common.exception.ErrorCode;
import com.example.adplatform.admin.response.PageResponse;
import com.example.adplatform.common.id.PublicIdGenerator;
import com.example.adplatform.search.candidate.event.ConfigStopGuardEvent;
import com.example.adplatform.search.outbox.message.ConfigAggregateType;
import com.example.adplatform.search.outbox.service.SearchOutboxService;
import lombok.RequiredArgsConstructor;
import org.springframework.dao.DuplicateKeyException;
import org.springframework.stereotype.Service;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;

/**
 * 广告计划管理及状态流转服务。
 *
 * <p>所有影响投放的写操作都会在当前事务内追加 Outbox。暂停/下线还会在提交后立即
 * 写 Redis 停投标记，待异步消费者完成 ES 同步后再清理该临时标记。</p>
 */
@RequiredArgsConstructor
@Service
public class PlanServiceImpl implements PlanService {

    private final PlanMapper planMapper;
    private final AdvertiserMapper advertiserMapper;
    private final PlanConverter planConverter;
    private final SearchOutboxService searchOutboxService;
    private final ApplicationEventPublisher applicationEventPublisher;
    private final EventMetadataPort eventMetadataCacheService;

    @Override
    @Transactional(rollbackFor = Exception.class)
    public PlanOperationResponse create(CreatePlanRequest request) {
        AdvertiserEntity advertiser = advertiserMapper.selectOne(new LambdaQueryWrapper<AdvertiserEntity>()
                .eq(AdvertiserEntity::getPublicId, request.advertiserPublicId()));
        if (advertiser == null) {
            throw new BusinessException(ErrorCode.RESOURCE_NOT_FOUND, "广告主不存在");
        }
        if (advertiser.getStatus() != CommonStatus.ENABLED) {
            throw new BusinessException(ErrorCode.INVALID_STATUS_TRANSITION, "广告主已停用");
        }
        PlanEntity entity = planConverter.createRequestToEntity(request);
        entity.initializePublicId(PublicIdGenerator.generate(PublicIdGenerator.PLAN_PREFIX));
        entity.setAdvertiserPublicId(request.advertiserPublicId());
        planMapper.insert(entity);
        return planConverter.entityToOperationResponse(entity);
    }

    @Override
    public PageResponse<PlanResponse> pageQuery(PagePlanRequest request) {
        Page<PlanEntity> page = new Page<>(request.current(), request.size());
        Page<PlanEntity> result = planMapper.selectPage(page, new LambdaQueryWrapper<PlanEntity>()
                .orderByAsc(PlanEntity::getName)
                .orderByDesc(PlanEntity::getId));
        return planConverter.pageToResponse(result);
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public PlanOperationResponse update(UpdatePlanRequest request) {
//        PlanEntity entity1 = planMapper.selectOne(new LambdaQueryWrapper<PlanEntity>()
//                .eq(PlanEntity::getPublicId, request.publicId()));
//        if (entity1 == null) {
//            throw new BusinessException(ErrorCode.RESOURCE_NOT_FOUND, "广告计划不存在");
//        }
        PlanEntity entity = planConverter.updateRequestToEntity(request);
        try {
            int updated = planMapper.update(entity, new LambdaUpdateWrapper<PlanEntity>()
                    .eq(PlanEntity::getPublicId, request.publicId()));
            if (updated == 0) {
                throw new BusinessException(ErrorCode.RESOURCE_NOT_FOUND, "广告计划不存在");
            }
        } catch (DuplicateKeyException ex) {
            throw new BusinessException(ErrorCode.DUPLICATE_RESOURCE, "该广告主下已存在同名计划");
        }
//        Long internalId = entity.getId();
//        eventMetadataCacheService.evictPlanAfterCommit(internalId);
//        searchOutboxService.appendConfigChange(ConfigAggregateType.PLAN, request.publicId());
        return planConverter.entityToOperationResponse(entity);
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public PlanOperationResponse delete(DeletePlanRequest request) {
        PlanEntity entity = getPlanOrThrow(request.publicId());
        if (planMapper.deleteById(entity.getId()) == 0) {
            throw new BusinessException(ErrorCode.RESOURCE_NOT_FOUND, "广告计划不存在");
        }
        eventMetadataCacheService.evictPlanAfterCommit(entity.getId());
        searchOutboxService.appendConfigChange(ConfigAggregateType.PLAN, request.publicId());
        return new PlanOperationResponse(request.publicId());
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public PlanResponse online(UpdatePlanStatusRequest request) {
        String publicId = request.publicId();
        PlanEntity entity = getPlanOrThrow(publicId);
        Long internalId = entity.getId();
        ensureAdvertiserEnabled(entity.getAdvertiserPublicId());
        if (entity.getEndTime().isBefore(LocalDateTime.now())) {
            throw new BusinessException(ErrorCode.INVALID_TIME_RANGE, "广告计划结束时间已过期");
        }
        entity.setStatus(PlanStatus.ONLINE.name());
        planMapper.updateById(entity);
        searchOutboxService.appendConfigChange(ConfigAggregateType.PLAN, publicId);
        applicationEventPublisher.publishEvent(new ConfigStopGuardEvent(ConfigAggregateType.PLAN, internalId, false));
        return toResponse(planMapper.selectById(internalId));
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public PlanResponse pause(UpdatePlanStatusRequest request) {
        String publicId = request.publicId();
        PlanEntity entity = getPlanOrThrow(publicId);
        Long internalId = entity.getId();
        if (!PlanStatus.ONLINE.name().equals(entity.getStatus())) {
            throw new BusinessException(ErrorCode.INVALID_STATUS_TRANSITION, "只有投放中的广告计划可以暂停");
        }
        entity.setStatus(PlanStatus.PAUSED.name());
        planMapper.updateById(entity);
        searchOutboxService.appendConfigChange(ConfigAggregateType.PLAN, publicId);
        applicationEventPublisher.publishEvent(new ConfigStopGuardEvent(ConfigAggregateType.PLAN, internalId, true));
        return toResponse(planMapper.selectById(internalId));
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public PlanResponse offline(UpdatePlanStatusRequest request) {
        String publicId = request.publicId();
        PlanEntity entity = getPlanOrThrow(publicId);
        Long internalId = entity.getId();
        entity.setStatus(PlanStatus.OFFLINE.name());
        planMapper.updateById(entity);
        searchOutboxService.appendConfigChange(ConfigAggregateType.PLAN, publicId);
        applicationEventPublisher.publishEvent(new ConfigStopGuardEvent(ConfigAggregateType.PLAN, internalId, true));
        return toResponse(planMapper.selectById(internalId));
    }

    private PlanEntity getPlanOrThrow(String publicId) {
        PlanEntity entity = planMapper.selectOne(new LambdaQueryWrapper<PlanEntity>()
                .eq(PlanEntity::getPublicId, publicId));
        if (entity == null) {
            throw new BusinessException(ErrorCode.RESOURCE_NOT_FOUND, "广告计划不存在");
        }
        return entity;
    }

    private PlanResponse toResponse(PlanEntity entity) {
        return planConverter.entityToQueryResponse(entity);
    }

    private void ensureAdvertiserEnabled(String advertiserPublicId) {
        AdvertiserEntity advertiser = advertiserMapper.selectOne(new LambdaQueryWrapper<AdvertiserEntity>()
                .eq(AdvertiserEntity::getPublicId, advertiserPublicId));
        if (advertiser == null) {
            throw new BusinessException(ErrorCode.RESOURCE_NOT_FOUND, "广告主不存在");
        }
        if (advertiser.getStatus() == null || advertiser.getStatus() != CommonStatus.ENABLED) {
            throw new BusinessException(ErrorCode.INVALID_STATUS_TRANSITION, "广告主已停用");
        }
    }
}
