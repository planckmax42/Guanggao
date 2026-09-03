package com.example.adplatform.admin.service.impl;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.example.adplatform.admin.converter.RuleConverter;
import com.example.adplatform.admin.request.CreateRuleRequest;
import com.example.adplatform.admin.entity.PlanEntity;
import com.example.adplatform.admin.entity.RuleEntity;
import com.example.adplatform.admin.mapper.PlanMapper;
import com.example.adplatform.admin.mapper.RuleMapper;
import com.example.adplatform.admin.service.RuleService;
import com.example.adplatform.admin.response.RuleResponse;
import com.example.adplatform.common.exception.BusinessException;
import com.example.adplatform.common.exception.ErrorCode;
import com.example.adplatform.common.response.ResourceRefResponse;
import com.example.adplatform.common.id.PublicIdGenerator;
import com.example.adplatform.search.outbox.message.ConfigAggregateType;
import com.example.adplatform.search.outbox.service.SearchOutboxService;
import lombok.RequiredArgsConstructor;
import org.springframework.dao.DuplicateKeyException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * 计划定向规则的创建或更新服务。
 *
 * <p>Outbox 使用 rulePublicId 作为 RULE 聚合标识；消费者在本地解析所属计划后，
 * 重建该计划下的全部素材文档。</p>
 */
@RequiredArgsConstructor
@Service
public class RuleServiceImpl implements RuleService {

    private final RuleMapper ruleMapper;
    private final PlanMapper planMapper;
    private final RuleConverter ruleConverter;
    private final SearchOutboxService searchOutboxService;

    @Override
    @Transactional(rollbackFor = Exception.class)
    public ResourceRefResponse createOrUpdate(CreateRuleRequest request) {
        PlanEntity plan = getPlanByPublicId(request.planPublicId());
        if (plan == null) {
            throw new BusinessException(ErrorCode.RESOURCE_NOT_FOUND, "广告计划不存在");
        }

        RuleEntity entity = ruleMapper.selectOne(new LambdaQueryWrapper<RuleEntity>()
                .eq(RuleEntity::getPlanId, plan.getId()));
        if (entity == null) {
            entity = ruleConverter.toEntity(request);
            entity.initializePublicId(PublicIdGenerator.generate(PublicIdGenerator.RULE_PREFIX));
            entity.setPlanId(plan.getId());
            try {
                ruleMapper.insert(entity);
            } catch (DuplicateKeyException ex) {
                entity = ruleMapper.selectOne(new LambdaQueryWrapper<RuleEntity>()
                        .eq(RuleEntity::getPlanId, plan.getId()));
                if (entity == null) {
                    throw new BusinessException(ErrorCode.DUPLICATE_RESOURCE, "定向规则已存在");
                }
                ruleConverter.updateEntity(request, entity);
                ruleMapper.updateById(entity);
            }
            searchOutboxService.appendConfigChange(ConfigAggregateType.RULE, entity.getPublicId());
            return ruleConverter.toRef(entity, plan.getPublicId());
        }

        ruleConverter.updateEntity(request, entity);
        ruleMapper.updateById(entity);
        searchOutboxService.appendConfigChange(ConfigAggregateType.RULE, entity.getPublicId());
        return ruleConverter.toRef(entity, plan.getPublicId());
    }

    @Override
    public RuleResponse getByPlanPublicId(String planPublicId) {
        PlanEntity plan = getPlanByPublicId(planPublicId);
        RuleEntity entity = ruleMapper.selectOne(new LambdaQueryWrapper<RuleEntity>()
                .eq(RuleEntity::getPlanId, plan.getId()));
        if (entity == null) {
            throw new BusinessException(ErrorCode.RESOURCE_NOT_FOUND, "定向规则不存在");
        }
        return ruleConverter.toResponse(entity, plan.getPublicId());
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
