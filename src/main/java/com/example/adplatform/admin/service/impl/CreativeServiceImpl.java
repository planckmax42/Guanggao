package com.example.adplatform.admin.service.impl;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.example.adplatform.admin.dto.AuditCreativeRequest;
import com.example.adplatform.admin.dto.CreateCreativeRequest;
import com.example.adplatform.admin.entity.AdSlotEntity;
import com.example.adplatform.admin.entity.CampaignEntity;
import com.example.adplatform.admin.entity.CreativeAuditStatus;
import com.example.adplatform.admin.entity.CreativeEntity;
import com.example.adplatform.admin.mapper.AdSlotMapper;
import com.example.adplatform.admin.mapper.CampaignMapper;
import com.example.adplatform.admin.mapper.CreativeMapper;
import com.example.adplatform.admin.service.CreativeService;
import com.example.adplatform.admin.vo.CreativeVO;
import com.example.adplatform.common.enums.CommonStatus;
import com.example.adplatform.common.exception.BusinessException;
import com.example.adplatform.common.exception.ErrorCode;
import com.example.adplatform.common.response.PageResponse;
import com.example.adplatform.common.response.ResourceRefVO;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;

import java.util.List;

@RequiredArgsConstructor
@Service
public class CreativeServiceImpl implements CreativeService {

    private final CreativeMapper creativeMapper;
    private final CampaignMapper campaignMapper;
    private final AdSlotMapper adSlotMapper;

    @Override
    public ResourceRefVO create(CreateCreativeRequest request) {
        ensureCampaignExists(request.campaignId());
        ensureAdSlotExists(request.adSlotId());

        CreativeEntity entity = new CreativeEntity();
        entity.setCampaignId(request.campaignId());
        entity.setAdSlotId(request.adSlotId());
        entity.setTitle(request.title());
        entity.setDescription(request.description());
        entity.setImageUrl(request.imageUrl());
        entity.setLandingPageUrl(request.landingPageUrl());
        entity.setAuditStatus(CreativeAuditStatus.PENDING.name());
        entity.setStatus(CommonStatus.ENABLED);
        creativeMapper.insert(entity);
        return new ResourceRefVO(entity.getId(), entity.getTitle());
    }

    @Override
    public CreativeVO audit(Long id, AuditCreativeRequest request) {
        CreativeEntity entity = getCreativeOrThrow(id);
        entity.setAuditStatus(request.auditStatus());
        creativeMapper.updateById(entity);
        return toVO(creativeMapper.selectById(id));
    }

    @Override
    public PageResponse<CreativeVO> pageQuery(long current, long size, Long campaignId, String auditStatus) {
        Page<CreativeEntity> page = new Page<>(current, size);
        LambdaQueryWrapper<CreativeEntity> query = new LambdaQueryWrapper<CreativeEntity>()
                .eq(campaignId != null, CreativeEntity::getCampaignId, campaignId)
                .eq(StringUtils.hasText(auditStatus), CreativeEntity::getAuditStatus, auditStatus)
                .orderByDesc(CreativeEntity::getId);
        Page<CreativeEntity> result = creativeMapper.selectPage(page, query);
        List<CreativeVO> records = result.getRecords().stream().map(this::toVO).toList();
        return PageResponse.of(result, records);
    }

    private void ensureCampaignExists(Long campaignId) {
        CampaignEntity campaign = campaignMapper.selectById(campaignId);
        if (campaign == null) {
            throw new BusinessException(ErrorCode.RESOURCE_NOT_FOUND, "广告计划不存在");
        }
    }

    private void ensureAdSlotExists(Long adSlotId) {
        AdSlotEntity adSlot = adSlotMapper.selectById(adSlotId);
        if (adSlot == null) {
            throw new BusinessException(ErrorCode.RESOURCE_NOT_FOUND, "广告位不存在");
        }
    }

    private CreativeEntity getCreativeOrThrow(Long id) {
        CreativeEntity entity = creativeMapper.selectById(id);
        if (entity == null) {
            throw new BusinessException(ErrorCode.RESOURCE_NOT_FOUND, "广告素材不存在");
        }
        return entity;
    }

    private CreativeVO toVO(CreativeEntity entity) {
        return new CreativeVO(
                entity.getId(),
                entity.getCampaignId(),
                entity.getAdSlotId(),
                entity.getTitle(),
                entity.getDescription(),
                entity.getImageUrl(),
                entity.getLandingPageUrl(),
                entity.getAuditStatus(),
                entity.getStatus(),
                entity.getCreatedAt(),
                entity.getUpdatedAt());
    }
}
