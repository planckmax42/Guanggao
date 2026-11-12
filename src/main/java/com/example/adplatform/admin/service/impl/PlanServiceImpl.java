package com.example.adplatform.admin.service.impl;

import com.example.adplatform.admin.request.plan.DeletePlanRequest;
import com.example.adplatform.admin.request.plan.PagePlanRequest;
import com.example.adplatform.admin.request.plan.UpdatePlanStatusRequest;
import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.example.adplatform.admin.converter.PlanConverter;
import com.example.adplatform.admin.util.AdvertiserGuardUtils;
import com.example.adplatform.admin.request.plan.CreatePlanRequest;
import com.example.adplatform.admin.request.plan.UpdatePlanRequest;
import com.example.adplatform.admin.entity.PlanEntity;
import com.example.adplatform.admin.enums.PlanStatus;
import com.example.adplatform.admin.mapper.PlanMapper;
import com.example.adplatform.admin.response.plan.PlanOperationResponse;
import com.example.adplatform.admin.service.PlanService;
import com.example.adplatform.admin.response.plan.PlanResponse;
import com.example.adplatform.common.exception.BusinessException;
import com.example.adplatform.common.exception.ErrorCode;
import com.example.adplatform.common.util.MySqlForeignKeyUtils;
import com.example.adplatform.admin.response.PageResponse;
import com.example.adplatform.search.candidate.event.ConfigStopGuardEvent;
import com.example.adplatform.search.outbox.message.ConfigAggregateType;
import lombok.RequiredArgsConstructor;
import org.springframework.dao.DuplicateKeyException;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.stereotype.Service;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;

/**
 * 广告计划管理及状态流转服务。
 *
 * <p>雪花 ID 迁移期间暂停旧 publicId Outbox 同步；基于数值 ID 的停投事件保留。</p>
 */
@RequiredArgsConstructor
@Service
public class PlanServiceImpl implements PlanService {

    private final PlanMapper planMapper;
    private final AdvertiserGuardUtils advertiserGuardUtils;
    private final PlanConverter planConverter;
    // Outbox 尚使用旧 publicId 契约，待 search 组件迁移后恢复同步。
    // private final SearchOutboxService searchOutboxService;
    private final ApplicationEventPublisher applicationEventPublisher;

    @Override
    @Transactional(rollbackFor = Exception.class)
    public PlanOperationResponse create(CreatePlanRequest request) {
        advertiserGuardUtils.requireEnabled(request.advertiserId());
        PlanEntity entity = planConverter.createRequestToEntity(request);

        try{
            planMapper.insert(entity);
        } catch (DuplicateKeyException ex) {
            throw new BusinessException(ErrorCode.DUPLICATE_RESOURCE, "该广告主下已存在同名计划");
        } catch (DataIntegrityViolationException ex) {
            if (MySqlForeignKeyUtils.isReferencedRowMissing(ex)) {
                throw new BusinessException(ErrorCode.RESOURCE_NOT_FOUND, "广告主不存在", ex);
            }
            throw ex;
        }
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
        PlanEntity existing = getPlanOrThrow(request.id());
        boolean changed = (request.name() != null && !request.name().equals(existing.getName()))
                || (request.budgetTotal() != null && !request.budgetTotal().equals(existing.getBudgetTotal()))
                || (request.budgetDaily() != null && !request.budgetDaily().equals(existing.getBudgetDaily()))
                || (request.bidPrice() != null && !request.bidPrice().equals(existing.getBidPrice()))
                || (request.billingType() != null && !request.billingType().equals(existing.getBillingType()))
                || (request.startTime() != null && !request.startTime().equals(existing.getStartTime()))
                || (request.endTime() != null && !request.endTime().equals(existing.getEndTime()));
        if (!changed) {
            return new PlanOperationResponse(request.id());
        }
        Long budgetTotal = request.budgetTotal() != null ? request.budgetTotal() : existing.getBudgetTotal();
        Long budgetDaily = request.budgetDaily() != null ? request.budgetDaily() : existing.getBudgetDaily();
        if (budgetDaily > budgetTotal) {
            throw new BusinessException(ErrorCode.INVALID_BUDGET, "日预算必须小于等于总预算");
        }
        LocalDateTime startTime = request.startTime() != null ? request.startTime() : existing.getStartTime();
        LocalDateTime endTime = request.endTime() != null ? request.endTime() : existing.getEndTime();
        if (!endTime.isAfter(startTime)) {
            throw new BusinessException(ErrorCode.INVALID_TIME_RANGE, "结束时间必须晚于开始时间");
        }
        PlanEntity entity = planConverter.updateRequestToEntity(request);
        entity.setId(existing.getId());
        try {
            if (planMapper.updateById(entity) == 0) {
                throw new BusinessException(ErrorCode.RESOURCE_NOT_FOUND, "广告计划不存在");
            }
        } catch (DuplicateKeyException ex) {
            throw new BusinessException(ErrorCode.DUPLICATE_RESOURCE, "该广告主下已存在同名计划");
        }
//        eventMetadataCacheService.evictPlanAfterCommit(entity.getId());
//        searchOutboxService.appendConfigChange(ConfigAggregateType.PLAN, request.publicId());
        return new PlanOperationResponse(request.id());
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public PlanOperationResponse delete(DeletePlanRequest request) {
        try {
            if (planMapper.deleteById(request.id()) == 0) {
                throw new BusinessException(ErrorCode.RESOURCE_NOT_FOUND, "广告计划不存在");
            }
        } catch (DataIntegrityViolationException ex) {
            if (MySqlForeignKeyUtils.isReferencedByOtherRows(ex)) {
                throw new BusinessException(ErrorCode.RESOURCE_IN_USE, "广告计划存在关联数据，不能删除", ex);
            }
            throw ex;
        }
        return new PlanOperationResponse(request.id());
//        eventMetadataCacheService.evictPlanAfterCommit(entity.getId());
//        searchOutboxService.appendConfigChange(ConfigAggregateType.PLAN, request.publicId());
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public PlanResponse online(UpdatePlanStatusRequest request) {
        Long id = request.id();
        PlanEntity entity = getPlanOrThrow(id);
        advertiserGuardUtils.requireEnabled(entity.getAdvertiserId());
        if (entity.getEndTime().isBefore(LocalDateTime.now())) {
            throw new BusinessException(ErrorCode.INVALID_TIME_RANGE, "广告计划结束时间已过期");
        }
        entity.setStatus(PlanStatus.ONLINE.name());
        planMapper.updateById(entity);
        // searchOutboxService.appendConfigChange(ConfigAggregateType.PLAN, publicId);
        applicationEventPublisher.publishEvent(new ConfigStopGuardEvent(ConfigAggregateType.PLAN, id, false));
        return toResponse(planMapper.selectById(id));
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public PlanResponse pause(UpdatePlanStatusRequest request) {
        Long id = request.id();
        PlanEntity entity = getPlanOrThrow(id);
        if (!PlanStatus.ONLINE.name().equals(entity.getStatus())) {
            throw new BusinessException(ErrorCode.INVALID_STATUS_TRANSITION, "只有投放中的广告计划可以暂停");
        }
        entity.setStatus(PlanStatus.PAUSED.name());
        planMapper.updateById(entity);
        // searchOutboxService.appendConfigChange(ConfigAggregateType.PLAN, publicId);
        applicationEventPublisher.publishEvent(new ConfigStopGuardEvent(ConfigAggregateType.PLAN, id, true));
        return toResponse(planMapper.selectById(id));
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public PlanResponse offline(UpdatePlanStatusRequest request) {
        Long id = request.id();
        PlanEntity entity = getPlanOrThrow(id);
        entity.setStatus(PlanStatus.OFFLINE.name());
        planMapper.updateById(entity);
        // searchOutboxService.appendConfigChange(ConfigAggregateType.PLAN, publicId);
        applicationEventPublisher.publishEvent(new ConfigStopGuardEvent(ConfigAggregateType.PLAN, id, true));
        return toResponse(planMapper.selectById(id));
    }

    private PlanEntity getPlanOrThrow(Long id) {
        PlanEntity entity = planMapper.selectById(id);
        if (entity == null) {
            throw new BusinessException(ErrorCode.RESOURCE_NOT_FOUND, "广告计划不存在");
        }
        return entity;
    }

    private PlanResponse toResponse(PlanEntity entity) {
        return planConverter.entityToQueryResponse(entity);
    }
}
