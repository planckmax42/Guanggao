package com.example.adplatform.admin.service.impl;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.example.adplatform.admin.converter.CampaignConverter;
import com.example.adplatform.admin.dto.CreateCampaignRequest;
import com.example.adplatform.admin.dto.UpdateCampaignRequest;
import com.example.adplatform.admin.entity.AdvertiserEntity;
import com.example.adplatform.admin.entity.CampaignEntity;
import com.example.adplatform.admin.entity.CampaignStatus;
import com.example.adplatform.admin.mapper.AdvertiserMapper;
import com.example.adplatform.admin.mapper.CampaignMapper;
import com.example.adplatform.admin.service.CampaignService;
import com.example.adplatform.admin.vo.CampaignVO;
import com.example.adplatform.common.enums.CommonStatus;
import com.example.adplatform.common.exception.BusinessException;
import com.example.adplatform.common.exception.ErrorCode;
import com.example.adplatform.common.response.PageResponse;
import com.example.adplatform.common.response.ResourceRefVO;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;

import java.time.LocalDateTime;
import java.util.List;

@RequiredArgsConstructor
@Service
public class CampaignServiceImpl implements CampaignService {

    private final CampaignMapper campaignMapper;
    private final AdvertiserMapper advertiserMapper;
    private final CampaignConverter campaignConverter;

    @Override
    public ResourceRefVO create(CreateCampaignRequest request) {
        ensureAdvertiserEnabled(request.advertiserId());

        CampaignEntity entity = campaignConverter.toEntity(request);
        campaignMapper.insert(entity);
        return campaignConverter.toRef(entity);
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public CampaignVO update(Long id, UpdateCampaignRequest request) {
        CampaignEntity entity = getCampaignOrThrow(id);
        if (CampaignStatus.OFFLINE.name().equals(entity.getStatus())) {
            throw new BusinessException(ErrorCode.INVALID_STATUS_TRANSITION, "已下线的广告计划不能修改");
        }
        campaignConverter.updateEntity(request, entity);
        campaignMapper.updateById(entity);
        return campaignConverter.toVO(campaignMapper.selectById(id));
    }

    @Override
    public CampaignVO online(Long id) {
        CampaignEntity entity = getCampaignOrThrow(id);
        ensureAdvertiserEnabled(entity.getAdvertiserId());
        if (entity.getEndTime().isBefore(LocalDateTime.now())) {
            throw new BusinessException(ErrorCode.INVALID_TIME_RANGE, "广告计划结束时间已过期");
        }
        entity.setStatus(CampaignStatus.ONLINE.name());
        campaignMapper.updateById(entity);
        return campaignConverter.toVO(campaignMapper.selectById(id));
    }

    @Override
    public CampaignVO pause(Long id) {
        CampaignEntity entity = getCampaignOrThrow(id);
        if (!CampaignStatus.ONLINE.name().equals(entity.getStatus())) {
            throw new BusinessException(ErrorCode.INVALID_STATUS_TRANSITION, "只有投放中的广告计划可以暂停");
        }
        entity.setStatus(CampaignStatus.PAUSED.name());
        campaignMapper.updateById(entity);
        return campaignConverter.toVO(campaignMapper.selectById(id));
    }

    @Override
    public CampaignVO offline(Long id) {
        CampaignEntity entity = getCampaignOrThrow(id);
        entity.setStatus(CampaignStatus.OFFLINE.name());
        campaignMapper.updateById(entity);
        return campaignConverter.toVO(campaignMapper.selectById(id));
    }

    @Override
    public PageResponse<CampaignVO> pageQuery(long current, long size, Long advertiserId, String status) {
        Page<CampaignEntity> page = new Page<>(current, size);
        LambdaQueryWrapper<CampaignEntity> query = new LambdaQueryWrapper<CampaignEntity>()
                .eq(advertiserId != null, CampaignEntity::getAdvertiserId, advertiserId)
                .eq(StringUtils.hasText(status), CampaignEntity::getStatus, status)
                .orderByDesc(CampaignEntity::getId);
        Page<CampaignEntity> result = campaignMapper.selectPage(page, query);
        List<CampaignVO> records = result.getRecords().stream().map(campaignConverter::toVO).toList();
        return PageResponse.of(result, records);
    }

    private CampaignEntity getCampaignOrThrow(Long id) {
        CampaignEntity entity = campaignMapper.selectById(id);
        if (entity == null) {
            throw new BusinessException(ErrorCode.RESOURCE_NOT_FOUND, "广告计划不存在");
        }
        return entity;
    }

    private void ensureAdvertiserEnabled(Long advertiserId) {
        AdvertiserEntity advertiser = advertiserMapper.selectById(advertiserId);
        if (advertiser == null) {
            throw new BusinessException(ErrorCode.RESOURCE_NOT_FOUND, "广告主不存在");
        }
        if (advertiser.getStatus() == null || advertiser.getStatus() != CommonStatus.ENABLED) {
            throw new BusinessException(ErrorCode.INVALID_STATUS_TRANSITION, "广告主已停用");
        }
    }
}
