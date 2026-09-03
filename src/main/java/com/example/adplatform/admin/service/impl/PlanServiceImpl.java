package com.example.adplatform.admin.service.impl;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.example.adplatform.admin.converter.PlanConverter;
import com.example.adplatform.admin.request.CreatePlanRequest;
import com.example.adplatform.admin.request.UpdatePlanRequest;
import com.example.adplatform.admin.entity.UserEntity;
import com.example.adplatform.admin.entity.PlanEntity;
import com.example.adplatform.admin.entity.PlanStatus;
import com.example.adplatform.admin.mapper.UserMapper;
import com.example.adplatform.admin.mapper.PlanMapper;
import com.example.adplatform.admin.port.EventMetadataCacheMaintenancePort;
import com.example.adplatform.admin.service.PlanService;
import com.example.adplatform.admin.response.PlanResponse;
import com.example.adplatform.common.enums.CommonStatus;
import com.example.adplatform.common.exception.BusinessException;
import com.example.adplatform.common.exception.ErrorCode;
import com.example.adplatform.common.response.PageResponse;
import com.example.adplatform.common.response.ResourceRefResponse;
import com.example.adplatform.common.id.PublicIdGenerator;
import com.example.adplatform.search.candidate.event.ConfigStopGuardEvent;
import com.example.adplatform.search.outbox.message.ConfigAggregateType;
import com.example.adplatform.search.outbox.service.SearchOutboxService;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

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
    private final UserMapper userMapper;
    private final PlanConverter planConverter;
    private final SearchOutboxService searchOutboxService;
    private final ApplicationEventPublisher applicationEventPublisher;
    private final EventMetadataCacheMaintenancePort eventMetadataCacheService;

    @Override
    @Transactional(rollbackFor = Exception.class)
    public ResourceRefResponse create(CreatePlanRequest request) {
        UserEntity advertiser = getEnabledAdvertiser(request.advertiserPublicId());

        PlanEntity entity = planConverter.toEntity(request);
        entity.initializePublicId(PublicIdGenerator.generate(PublicIdGenerator.PLAN_PREFIX));
        entity.setUserId(advertiser.getId());
        planMapper.insert(entity);
        searchOutboxService.appendConfigChange(ConfigAggregateType.PLAN, entity.getPublicId());
        return planConverter.toRef(entity);
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public PlanResponse update(String publicId, UpdatePlanRequest request) {
        PlanEntity entity = getPlanOrThrow(publicId);
        Long internalId = entity.getId();
        if (PlanStatus.OFFLINE.name().equals(entity.getStatus())) {
            throw new BusinessException(ErrorCode.INVALID_STATUS_TRANSITION, "已下线的广告计划不能修改");
        }
        planConverter.updateEntity(request, entity);
        planMapper.updateById(entity);
        eventMetadataCacheService.evictPlanAfterCommit(internalId);
        searchOutboxService.appendConfigChange(ConfigAggregateType.PLAN, publicId);
        return toResponse(planMapper.selectById(internalId));
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public PlanResponse online(String publicId) {
        PlanEntity entity = getPlanOrThrow(publicId);
        Long internalId = entity.getId();
        ensureUserEnabled(entity.getUserId());
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
    public PlanResponse pause(String publicId) {
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
    public PlanResponse offline(String publicId) {
        PlanEntity entity = getPlanOrThrow(publicId);
        Long internalId = entity.getId();
        entity.setStatus(PlanStatus.OFFLINE.name());
        planMapper.updateById(entity);
        searchOutboxService.appendConfigChange(ConfigAggregateType.PLAN, publicId);
        applicationEventPublisher.publishEvent(new ConfigStopGuardEvent(ConfigAggregateType.PLAN, internalId, true));
        return toResponse(planMapper.selectById(internalId));
    }

    @Override
    public PageResponse<PlanResponse> pageQuery(
            long current,
            long size,
            String advertiserPublicId,
            String status) {
        Long userId = advertiserPublicId == null ? null : getAdvertiser(advertiserPublicId).getId();
        Page<PlanEntity> page = new Page<>(current, size);
        LambdaQueryWrapper<PlanEntity> query = new LambdaQueryWrapper<PlanEntity>()
                .eq(userId != null, PlanEntity::getUserId, userId)
                .eq(StringUtils.hasText(status), PlanEntity::getStatus, status)
                .orderByDesc(PlanEntity::getId);
        Page<PlanEntity> result = planMapper.selectPage(page, query);
        List<Long> advertiserIds = result.getRecords().stream()
                .map(PlanEntity::getUserId)
                .distinct()
                .toList();
        Map<Long, String> advertiserPublicIds = advertiserIds.isEmpty()
                ? Map.of()
                : userMapper.selectBatchIds(advertiserIds).stream()
                        .collect(Collectors.toMap(UserEntity::getId, UserEntity::getPublicId));
        List<PlanResponse> records = result.getRecords().stream()
                .map(entity -> planConverter.toResponse(entity, advertiserPublicIds.get(entity.getUserId())))
                .toList();
        return PageResponse.of(result, records);
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
        UserEntity advertiser = userMapper.selectById(entity.getUserId());
        return planConverter.toResponse(entity, advertiser == null ? null : advertiser.getPublicId());
    }

    private UserEntity getAdvertiser(String publicId) {
        UserEntity advertiser = userMapper.selectOne(new LambdaQueryWrapper<UserEntity>()
                .eq(UserEntity::getPublicId, publicId));
        if (advertiser == null) {
            throw new BusinessException(ErrorCode.RESOURCE_NOT_FOUND, "广告主不存在");
        }
        return advertiser;
    }

    private UserEntity getEnabledAdvertiser(String publicId) {
        UserEntity advertiser = getAdvertiser(publicId);
        ensureUserEnabled(advertiser.getId());
        return advertiser;
    }

    private void ensureUserEnabled(Long userId) {
        UserEntity user = userMapper.selectById(userId);
        if (user == null) {
            throw new BusinessException(ErrorCode.RESOURCE_NOT_FOUND, "广告主不存在");
        }
        if (user.getStatus() == null || user.getStatus() != CommonStatus.ENABLED) {
            throw new BusinessException(ErrorCode.INVALID_STATUS_TRANSITION, "广告主已停用");
        }
    }
}
