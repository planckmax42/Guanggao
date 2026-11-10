package com.example.adplatform.admin.service.impl;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.conditions.update.LambdaUpdateWrapper;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.example.adplatform.admin.converter.RuleConverter;
import com.example.adplatform.admin.entity.PlanEntity;
import com.example.adplatform.admin.entity.RuleEntity;
import com.example.adplatform.admin.mapper.PlanMapper;
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
import com.example.adplatform.common.id.PublicIdGenerator;
import com.example.adplatform.search.outbox.message.ConfigAggregateType;
import com.example.adplatform.search.outbox.service.SearchOutboxService;
import lombok.RequiredArgsConstructor;
import org.springframework.dao.DuplicateKeyException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@RequiredArgsConstructor
@Service
public class RuleServiceImpl implements RuleService {

    private final RuleMapper ruleMapper;
    private final PlanMapper planMapper;
    private final RuleConverter ruleConverter;
    private final SearchOutboxService searchOutboxService;

    @Override
    @Transactional(rollbackFor = Exception.class)
    public RuleOperationResponse create(CreateRuleRequest request) {
        if (!planMapper.exists(new LambdaQueryWrapper<PlanEntity>()
                .eq(PlanEntity::getPublicId, request.planPublicId()))) {
            throw new BusinessException(ErrorCode.RESOURCE_NOT_FOUND, "广告计划不存在");
        }
        if (ruleMapper.exists(new LambdaQueryWrapper<RuleEntity>()
                .eq(RuleEntity::getPlanPublicId, request.planPublicId()))) {
            throw new BusinessException(ErrorCode.DUPLICATE_RESOURCE, "定向规则已存在");
        }
        RuleEntity entity = ruleConverter.requestToEntity(request);
        entity.initializePublicId(PublicIdGenerator.generate(PublicIdGenerator.RULE_PREFIX));
        try {
            ruleMapper.insert(entity);
        } catch (DuplicateKeyException ex) {
            throw new BusinessException(ErrorCode.DUPLICATE_RESOURCE, "定向规则已存在");
        }
        searchOutboxService.appendConfigChange(ConfigAggregateType.RULE, entity.getPublicId());
        return ruleConverter.entityToOperationResponse(entity);
    }

    @Override
    public PageResponse<RuleResponse> pageQuery(PageRuleRequest request) {
        Page<RuleEntity> result = ruleMapper.selectPage(new Page<>(request.current(), request.size()),
                new LambdaQueryWrapper<RuleEntity>().orderByDesc(RuleEntity::getId));
        return ruleConverter.pageToResponse(result);
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public RuleOperationResponse update(UpdateRuleRequest request) {
        RuleEntity entity = getRuleOrThrow(request.publicId());
        ruleConverter.requestToEntity(request, entity);
        // 空值表示取消该项定向，需要显式写入 NULL。
        ruleMapper.update(new RuleEntity(), new LambdaUpdateWrapper<RuleEntity>()
                .eq(RuleEntity::getPublicId, request.publicId())
                .set(RuleEntity::getRegion, entity.getRegion())
                .set(RuleEntity::getDeviceType, entity.getDeviceType())
                .set(RuleEntity::getGender, entity.getGender())
                .set(RuleEntity::getAgeMin, entity.getAgeMin())
                .set(RuleEntity::getAgeMax, entity.getAgeMax())
                .set(RuleEntity::getUserTags, entity.getUserTags()));
        searchOutboxService.appendConfigChange(ConfigAggregateType.RULE, request.publicId());
        return ruleConverter.entityToOperationResponse(entity);
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public RuleOperationResponse delete(DeleteRuleRequest request) {
        RuleEntity entity = getRuleOrThrow(request.publicId());
        if (ruleMapper.deleteById(entity.getId()) == 0) {
            throw new BusinessException(ErrorCode.RESOURCE_NOT_FOUND, "定向规则不存在");
        }
        // 删除后无法再从规则解析所属计划，使用计划公开标识触发重建。
        searchOutboxService.appendConfigChange(ConfigAggregateType.PLAN, entity.getPlanPublicId());
        return ruleConverter.entityToOperationResponse(entity);
    }

    private RuleEntity getRuleOrThrow(String publicId) {
        RuleEntity entity = ruleMapper.selectOne(new LambdaQueryWrapper<RuleEntity>()
                .eq(RuleEntity::getPublicId, publicId));
        if (entity == null) {
            throw new BusinessException(ErrorCode.RESOURCE_NOT_FOUND, "定向规则不存在");
        }
        return entity;
    }
}
