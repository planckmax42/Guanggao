package com.example.adplatform.admin.service.impl;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
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
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.RequiredArgsConstructor;
import org.springframework.dao.DuplicateKeyException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

@RequiredArgsConstructor
@Service
public class TargetingRuleServiceImpl implements TargetingRuleService {

    private static final TypeReference<List<String>> STRING_LIST_TYPE = new TypeReference<>() {
    };

    private final TargetingRuleMapper targetingRuleMapper;
    private final CampaignMapper campaignMapper;
    private final ObjectMapper objectMapper;

    @Override
    @Transactional(rollbackFor = Exception.class)
    public ResourceRefVO createOrUpdate(CreateTargetingRuleRequest request) {
        ensureCampaignExists(request.campaignId());

        TargetingRuleEntity entity = targetingRuleMapper.selectOne(new LambdaQueryWrapper<TargetingRuleEntity>()
                .eq(TargetingRuleEntity::getCampaignId, request.campaignId()));
        if (entity == null) {
            entity = new TargetingRuleEntity();
            entity.setCampaignId(request.campaignId());
            fillEntity(entity, request);
            try {
                targetingRuleMapper.insert(entity);
            } catch (DuplicateKeyException ex) {
                entity = getByCampaignIdForUpdate(request.campaignId());
                fillEntity(entity, request);
                targetingRuleMapper.updateById(entity);
            }
            return new ResourceRefVO(entity.getId(), String.valueOf(entity.getCampaignId()));
        }

        fillEntity(entity, request);
        targetingRuleMapper.updateById(entity);
        return new ResourceRefVO(entity.getId(), String.valueOf(entity.getCampaignId()));
    }

    @Override
    public TargetingRuleVO getByCampaignId(Long campaignId) {
        TargetingRuleEntity entity = targetingRuleMapper.selectOne(new LambdaQueryWrapper<TargetingRuleEntity>()
                .eq(TargetingRuleEntity::getCampaignId, campaignId));
        if (entity == null) {
            throw new BusinessException(ErrorCode.RESOURCE_NOT_FOUND, "定向规则不存在");
        }
        return toVO(entity);
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

    private void fillEntity(TargetingRuleEntity entity, CreateTargetingRuleRequest request) {
        entity.setRegion(toJson(request.regions()));
        entity.setDeviceType(toJson(request.deviceTypes()));
        entity.setGender(request.gender());
        entity.setAgeMin(request.ageMin());
        entity.setAgeMax(request.ageMax());
        entity.setUserTags(toJson(request.userTags()));
    }

    private String toJson(List<String> values) {
        if (values == null || values.isEmpty()) {
            return null;
        }
        try {
            return objectMapper.writeValueAsString(values);
        } catch (JsonProcessingException ex) {
            throw new BusinessException(ErrorCode.BUSINESS_ERROR, "定向规则序列化失败");
        }
    }

    private List<String> fromJson(String value) {
        if (value == null || value.isBlank()) {
            return List.of();
        }
        try {
            return objectMapper.readValue(value, STRING_LIST_TYPE);
        } catch (JsonProcessingException ex) {
            throw new BusinessException(ErrorCode.BUSINESS_ERROR, "定向规则解析失败");
        }
    }

    private TargetingRuleVO toVO(TargetingRuleEntity entity) {
        return new TargetingRuleVO(
                entity.getId(),
                entity.getCampaignId(),
                fromJson(entity.getRegion()),
                fromJson(entity.getDeviceType()),
                entity.getGender(),
                entity.getAgeMin(),
                entity.getAgeMax(),
                fromJson(entity.getUserTags()),
                entity.getCreatedAt(),
                entity.getUpdatedAt());
    }
}
