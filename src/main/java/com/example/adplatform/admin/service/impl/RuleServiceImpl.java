package com.example.adplatform.admin.service.impl;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.example.adplatform.admin.converter.RuleConverter;
import com.example.adplatform.admin.dto.CreateRuleRequest;
import com.example.adplatform.admin.entity.PlanEntity;
import com.example.adplatform.admin.entity.RuleEntity;
import com.example.adplatform.admin.mapper.PlanMapper;
import com.example.adplatform.admin.mapper.RuleMapper;
import com.example.adplatform.admin.service.RuleService;
import com.example.adplatform.admin.vo.RuleVO;
import com.example.adplatform.common.exception.BusinessException;
import com.example.adplatform.common.exception.ErrorCode;
import com.example.adplatform.common.response.ResourceRefVO;
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
    public ResourceRefVO createOrUpdate(CreateRuleRequest request) {
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
    public RuleVO getByPlanId(Long planId) {
        RuleEntity entity = ruleMapper.selectOne(new LambdaQueryWrapper<RuleEntity>()
                .eq(RuleEntity::getPlanId, planId));
        if (entity == null) {
            throw new BusinessException(ErrorCode.RESOURCE_NOT_FOUND, "定向规则不存在");
        }
        return ruleConverter.toVO(entity);
    }
}
