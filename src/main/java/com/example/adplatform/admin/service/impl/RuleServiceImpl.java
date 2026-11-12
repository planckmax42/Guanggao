package com.example.adplatform.admin.service.impl;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.conditions.update.LambdaUpdateWrapper;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.baomidou.mybatisplus.extension.handlers.JacksonTypeHandler;
import com.example.adplatform.admin.converter.RuleConverter;
import com.example.adplatform.admin.entity.RuleEntity;
import com.example.adplatform.admin.util.AdvertiserGuardUtils;
import com.example.adplatform.admin.mapper.RuleMapper;
import com.example.adplatform.admin.request.rule.CreateRuleRequest;
import com.example.adplatform.admin.request.rule.UpdateRuleRequest;
import com.example.adplatform.admin.request.rule.DeleteRuleRequest;
import com.example.adplatform.admin.request.rule.PageRuleRequest;
import com.example.adplatform.admin.response.PageResponse;
import com.example.adplatform.admin.response.rule.RuleOperationResponse;
import com.example.adplatform.admin.response.rule.RuleResponse;
import com.example.adplatform.admin.service.RuleService;
import com.example.adplatform.common.exception.BusinessException;
import com.example.adplatform.common.exception.ErrorCode;
import com.example.adplatform.common.util.MySqlForeignKeyUtils;
import com.example.adplatform.common.util.PublicIdUtils;
import lombok.RequiredArgsConstructor;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.dao.DuplicateKeyException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.Objects;
import java.util.List;

@RequiredArgsConstructor
@Service
public class RuleServiceImpl implements RuleService {

    private final RuleMapper ruleMapper;
    private final RuleConverter ruleConverter;
    private final AdvertiserGuardUtils advertiserGuardUtils;
    // TODO：后续恢复规则变更同步，并通过关联表定位受影响的计划。
    // private final SearchOutboxService searchOutboxService;

    @Override
    @Transactional(rollbackFor = Exception.class)
    public RuleOperationResponse create(CreateRuleRequest request) {
        advertiserGuardUtils.requireEnabled(request.advertiserPublicId());
        RuleEntity entity = ruleConverter.createRequestToEntity(request);
        entity.initializePublicId(PublicIdUtils.generate(PublicIdUtils.RULE_PREFIX));
        try {
            ruleMapper.insert(entity);
        } catch (DuplicateKeyException ex) {
            throw new BusinessException(ErrorCode.DUPLICATE_RESOURCE, "该广告主下已存在同名定向规则");
        } catch (DataIntegrityViolationException ex) {
            if (MySqlForeignKeyUtils.isReferencedRowMissing(ex)) {
                throw new BusinessException(ErrorCode.RESOURCE_NOT_FOUND, "广告主不存在", ex);
            }
            throw ex;
        }
        // searchOutboxService.appendConfigChange(ConfigAggregateType.RULE, entity.getPublicId());
        return ruleConverter.entityToOperationResponse(entity);
    }

    @Override
    public PageResponse<RuleResponse> pageQuery(PageRuleRequest request) {
        Page<RuleEntity> page = new Page<>(request.current(), request.size());
        Page<RuleEntity> result = ruleMapper.selectPage(page,
                new LambdaQueryWrapper<RuleEntity>().orderByDesc(RuleEntity::getId));
        return ruleConverter.pageToResponse(result);
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public RuleOperationResponse update(UpdateRuleRequest request) {
        RuleEntity existing = ruleMapper.selectOne(new LambdaQueryWrapper<RuleEntity>()
                .eq(RuleEntity::getPublicId, request.publicId()));
        if (existing == null) {
            throw new BusinessException(ErrorCode.RESOURCE_NOT_FOUND, "定向规则不存在");
        }
        List<String> currentDevices = existing.getDeviceTypes() == null ? List.of() : existing.getDeviceTypes();//？
        boolean changed = (request.name() != null && !request.name().equals(existing.getName()))
                || (request.deviceTypes() != null && !request.deviceTypes().equals(currentDevices))
                || (request.gender() != null && !Objects.equals(request.gender(), existing.getGender()))
                || (request.ageMin() != null && !request.ageMin().equals(existing.getAgeMin()))
                || (request.ageMax() != null && !request.ageMax().equals(existing.getAgeMax()));
        if (!changed) {
            return new RuleOperationResponse(request.publicId());
        }
        Integer ageMin = request.ageMin() != null ? request.ageMin() : existing.getAgeMin();
        Integer ageMax = request.ageMax() != null ? request.ageMax() : existing.getAgeMax();
        if (ageMin != null && ageMax != null && ageMax < ageMin) {
            throw new BusinessException(ErrorCode.PARAM_VALIDATION_FAILED, "最大年龄必须大于等于最小年龄");
        }
        RuleEntity entity = ruleConverter.updateRequestToEntity(request);
        // null 表示不修改；显式传空列表时需要将对应列设为 NULL。
        if (entity.getDeviceTypes() != null && entity.getDeviceTypes().isEmpty()) {
            entity.setDeviceTypes(null);//?
        }
        try {
            int updated = ruleMapper.update(new RuleEntity(), new LambdaUpdateWrapper<RuleEntity>()
                    .eq(RuleEntity::getId, existing.getId())
                    .set(request.name() != null, RuleEntity::getName, entity.getName())
                    .set(request.deviceTypes() != null, RuleEntity::getDeviceTypes, entity.getDeviceTypes(),
                            "jdbcType=VARCHAR,typeHandler=" + JacksonTypeHandler.class.getName())
                    .set(request.gender() != null, RuleEntity::getGender, entity.getGender())
                    .set(request.ageMin() != null, RuleEntity::getAgeMin, entity.getAgeMin())
                    .set(request.ageMax() != null, RuleEntity::getAgeMax, entity.getAgeMax()));//?
            if (updated == 0) {
                throw new BusinessException(ErrorCode.RESOURCE_NOT_FOUND, "定向规则不存在");
            }
        } catch (DuplicateKeyException ex) {
            throw new BusinessException(ErrorCode.DUPLICATE_RESOURCE, "该广告主下已存在同名定向规则");
        }
        // 原规则迁移计划的同步逻辑，恢复时应改为通过关联表重建受影响的计划：
        // if (!oldPlanPublicId.equals(entity.getPlanPublicId())) {
        //     searchOutboxService.appendConfigChange(ConfigAggregateType.PLAN, oldPlanPublicId);
        // }
        // searchOutboxService.appendConfigChange(ConfigAggregateType.RULE, request.publicId());
        return new RuleOperationResponse(request.publicId());
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public RuleOperationResponse delete(DeleteRuleRequest request) {
        try {
            if (ruleMapper.delete(new LambdaQueryWrapper<RuleEntity>()
                    .eq(RuleEntity::getPublicId, request.publicId())) == 0) {
                throw new BusinessException(ErrorCode.RESOURCE_NOT_FOUND, "定向规则不存在");
            }
        } catch (DataIntegrityViolationException ex) {
            if (MySqlForeignKeyUtils.isReferencedByOtherRows(ex)) {
                throw new BusinessException(ErrorCode.RESOURCE_IN_USE, "定向规则存在关联数据，不能删除", ex);
            }
            throw ex;
        }
        // 原删除同步逻辑，恢复时应先通过关联表确定受影响的计划：
        // searchOutboxService.appendConfigChange(ConfigAggregateType.PLAN, entity.getPlanPublicId());
        return new RuleOperationResponse(request.publicId());
    }
}
