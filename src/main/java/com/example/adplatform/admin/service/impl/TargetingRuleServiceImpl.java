package com.example.adplatform.admin.service.impl;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.example.adplatform.admin.converter.TargetingRuleConverter;
import com.example.adplatform.admin.dto.CreateTargetingRuleRequest;
import com.example.adplatform.admin.entity.CampaignEntity;
import com.example.adplatform.admin.entity.TargetingRuleEntity;
import com.example.adplatform.admin.mapper.CampaignMapper;
import com.example.adplatform.admin.mapper.TargetingRuleMapper;
import com.example.adplatform.admin.service.TargetingRuleService;
import com.example.adplatform.admin.vo.TargetingRuleVO;
import com.example.adplatform.common.exception.BusinessException;
import com.example.adplatform.common.exception.ErrorCode;
import com.example.adplatform.common.response.ResourceRefVO;
import lombok.RequiredArgsConstructor;
import org.springframework.dao.DuplicateKeyException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@RequiredArgsConstructor
@Service
public class TargetingRuleServiceImpl implements TargetingRuleService {

    private final TargetingRuleMapper targetingRuleMapper;
    private final CampaignMapper campaignMapper;
    private final TargetingRuleConverter targetingRuleConverter;

    @Override
    @Transactional(rollbackFor = Exception.class)
    public ResourceRefVO createOrUpdate(CreateTargetingRuleRequest request) {
        ensureCampaignExists(request.campaignId());

        TargetingRuleEntity entity = targetingRuleMapper.selectOne(new LambdaQueryWrapper<TargetingRuleEntity>()
                .eq(TargetingRuleEntity::getCampaignId, request.campaignId()));
        if (entity == null) {
            entity = targetingRuleConverter.toEntity(request);
            try {
                targetingRuleMapper.insert(entity);
            } catch (DuplicateKeyException ex) {
                entity = getByCampaignIdForUpdate(request.campaignId());
                targetingRuleConverter.updateEntity(request, entity);
                targetingRuleMapper.updateById(entity);
            }
            return targetingRuleConverter.toRef(entity);
        }

        targetingRuleConverter.updateEntity(request, entity);
        targetingRuleMapper.updateById(entity);
        return targetingRuleConverter.toRef(entity);
    }

    @Override
    public TargetingRuleVO getByCampaignId(Long campaignId) {
        TargetingRuleEntity entity = targetingRuleMapper.selectOne(new LambdaQueryWrapper<TargetingRuleEntity>()
                .eq(TargetingRuleEntity::getCampaignId, campaignId));
        if (entity == null) {
            throw new BusinessException(ErrorCode.RESOURCE_NOT_FOUND, "定向规则不存在");
        }
        return targetingRuleConverter.toVO(entity);
    }

    private void ensureCampaignExists(Long campaignId) {
        CampaignEntity campaign = campaignMapper.selectById(campaignId);
        if (campaign == null) {
            throw new BusinessException(ErrorCode.RESOURCE_NOT_FOUND, "广告计划不存在");
        }
    }

    private TargetingRuleEntity getByCampaignIdForUpdate(Long campaignId) {
        TargetingRuleEntity entity = targetingRuleMapper.selectOne(new LambdaQueryWrapper<TargetingRuleEntity>()
                .eq(TargetingRuleEntity::getCampaignId, campaignId));
        if (entity == null) {
            throw new BusinessException(ErrorCode.DUPLICATE_RESOURCE, "定向规则已存在");
        }
        return entity;
    }
}
