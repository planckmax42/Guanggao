package com.example.adplatform.admin.service.impl;

import com.example.adplatform.admin.request.advertiser.UpdateAdvertiserRequest;
import com.example.adplatform.admin.request.advertiser.DeleteAdvertiserRequest;
import com.example.adplatform.admin.request.advertiser.PageAdvertiserRequest;
import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.example.adplatform.admin.converter.AdvertiserConverter;
import com.example.adplatform.admin.request.advertiser.CreateAdvertiserRequest;
import com.example.adplatform.admin.entity.AdvertiserEntity;
import com.example.adplatform.admin.mapper.AdvertiserMapper;
import com.example.adplatform.admin.response.advertiser.AdvertiserOperationResponse;
import com.example.adplatform.admin.service.AdvertiserService;
import com.example.adplatform.admin.response.advertiser.AdvertiserQueryResponse;
import com.example.adplatform.common.enums.CommonStatus;
import com.example.adplatform.common.exception.BusinessException;
import com.example.adplatform.common.exception.ErrorCode;
import com.example.adplatform.admin.response.PageResponse;
import com.example.adplatform.common.id.PublicIdGenerator;
import lombok.RequiredArgsConstructor;
import org.springframework.dao.DuplicateKeyException;
import org.springframework.stereotype.Service;


@RequiredArgsConstructor
@Service
public class AdvertiserServiceImpl implements AdvertiserService {

    private final AdvertiserMapper advertiserMapper;
    private final AdvertiserConverter advertiserConverter;

    @Override
    public AdvertiserOperationResponse create(CreateAdvertiserRequest request) {
        AdvertiserEntity entity = advertiserConverter.createRequestToEntity(request);
        entity.initializePublicId(PublicIdGenerator.generate(PublicIdGenerator.ADVERTISER_PREFIX));
        try {
            advertiserMapper.insert(entity);
        } catch (DuplicateKeyException ex) {
            throw new BusinessException(ErrorCode.DUPLICATE_RESOURCE, "广告主名称已存在");
        }
        return advertiserConverter.entityToOperationResponse(entity);
    }

    @Override
    public PageResponse<AdvertiserQueryResponse> pageQuery(PageAdvertiserRequest request) {
        Page<AdvertiserEntity> page = new Page<>(request.current(), request.size());
        Page<AdvertiserEntity> result = advertiserMapper.selectPage(page,  new LambdaQueryWrapper<AdvertiserEntity>()
                .orderByAsc(AdvertiserEntity::getName)
                .orderByAsc(AdvertiserEntity::getId));
        return advertiserConverter.pageToResponse(result);
    }

    @Override
    public AdvertiserOperationResponse update(UpdateAdvertiserRequest request) {
        AdvertiserEntity advertiser = advertiserMapper.selectOne(new LambdaQueryWrapper<AdvertiserEntity>()
                .eq(AdvertiserEntity::getPublicId, request.publicId()));
        if (advertiser == null) {
            throw new BusinessException(ErrorCode.RESOURCE_NOT_FOUND, "广告主不存在");
        }
        if (advertiser.getStatus() != CommonStatus.ENABLED) {
            throw new BusinessException(ErrorCode.INVALID_STATUS_TRANSITION, "广告主已停用");
        }
        boolean changed = (request.name() != null && !request.name().equals(advertiser.getName()))
                || (request.industry() != null && !request.industry().equals(advertiser.getIndustry()))
                || (request.contactName() != null && !request.contactName().equals(advertiser.getContactName()))
                || (request.contactEmail() != null && !request.contactEmail().equals(advertiser.getContactEmail()));
        if (!changed) {
            return new AdvertiserOperationResponse(request.publicId());
        }
        AdvertiserEntity entity = advertiserConverter.updateRequestToEntity(request);
        entity.setId(advertiser.getId());
        try {
            if (advertiserMapper.updateById(entity) == 0) {
                throw new BusinessException(ErrorCode.RESOURCE_NOT_FOUND, "广告主不存在");
            }
        } catch (DuplicateKeyException ex) {
            throw new BusinessException(ErrorCode.DUPLICATE_RESOURCE, "广告主名称已存在");
        }
        return new AdvertiserOperationResponse(request.publicId());
    }

    @Override
    public AdvertiserOperationResponse delete(DeleteAdvertiserRequest request) {
        if (advertiserMapper.hasReferences(request.publicId())) {
            throw new BusinessException(ErrorCode.RESOURCE_IN_USE, "广告主存在关联数据，不能删除");
        }
        int deleted = advertiserMapper.delete(new LambdaQueryWrapper<AdvertiserEntity>()
                .eq(AdvertiserEntity::getPublicId, request.publicId()));
        if (deleted == 0) {
            throw new BusinessException(ErrorCode.RESOURCE_NOT_FOUND, "广告主不存在");
        }
        return new AdvertiserOperationResponse(request.publicId());
    }
}
