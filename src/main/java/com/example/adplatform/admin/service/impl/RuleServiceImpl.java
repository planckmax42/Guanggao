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
import com.example.adplatform.search.outbox.message.ConfigAggregateType;
import com.example.adplatform.search.outbox.service.SearchOutboxService;
import lombok.RequiredArgsConstructor;
import org.springframework.dao.DuplicateKeyException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * 计划定向规则的创建或更新服务。
 *
 * <p>候选文档按计划展开，所以 Outbox 的 aggregateId 使用 planId 而不是 ruleId；消费者
 * 收到消息后会重建该计划下的全部素材文档。</p>
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
        PlanEntity plan = planMapper.selectById(request.planId());
        if (plan == null) {
            throw new BusinessException(ErrorCode.RESOURCE_NOT_FOUND, "广告计划不存在");
        }

        RuleEntity entity = ruleMapper.selectOne(new LambdaQueryWrapper<RuleEntity>()
                .eq(RuleEntity::getPlanId, request.planId()));
        if (entity == null) {
            entity = ruleConverter.toEntity(request);
            try {
                ruleMapper.insert(entity);
            } catch (DuplicateKeyException ex) {
                entity = ruleMapper.selectOne(new LambdaQueryWrapper<RuleEntity>()
                        .eq(RuleEntity::getPlanId, request.planId()));
                if (entity == null) {
                    throw new BusinessException(ErrorCode.DUPLICATE_RESOURCE, "定向规则已存在");
                }
                ruleConverter.updateEntity(request, entity);
                ruleMapper.updateById(entity);
            }
            searchOutboxService.appendConfigChange(ConfigAggregateType.RULE, request.planId());
            return ruleConverter.toRef(entity);
        }

        ruleConverter.updateEntity(request, entity);
        ruleMapper.updateById(entity);
        searchOutboxService.appendConfigChange(ConfigAggregateType.RULE, request.planId());
        return ruleConverter.toRef(entity);
    }

    @Override
    public RuleResponse getByPlanId(Long planId) {
        RuleEntity entity = ruleMapper.selectOne(new LambdaQueryWrapper<RuleEntity>()
                .eq(RuleEntity::getPlanId, planId));
        if (entity == null) {
            throw new BusinessException(ErrorCode.RESOURCE_NOT_FOUND, "定向规则不存在");
        }
        return ruleConverter.toResponse(entity);
    }
}
