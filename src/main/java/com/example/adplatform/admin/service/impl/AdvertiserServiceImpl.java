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
        AdvertiserEntity entity = advertiserConverter.requestToEntity(request);
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
        long current = request.current();
        long size = request.size();
        Page<AdvertiserEntity> page = new Page<>(current, size);
        Page<AdvertiserEntity> result = advertiserMapper.selectPage(page,  new LambdaQueryWrapper<AdvertiserEntity>()
                .orderByAsc(AdvertiserEntity::getName)
                .orderByAsc(AdvertiserEntity::getId));
        return advertiserConverter.pageToResponse(result);
    }

    @Override
    public AdvertiserOperationResponse update(UpdateAdvertiserRequest request) {
        AdvertiserEntity existing = advertiserMapper.selectOne(new LambdaQueryWrapper<AdvertiserEntity>()
                .eq(AdvertiserEntity::getPublicId, request.publicId()));
        if (existing == null) {
            throw new BusinessException(ErrorCode.RESOURCE_NOT_FOUND, "广告主不存在");
        }
        AdvertiserEntity entity = advertiserConverter.requestToEntity(request);
        entity.setId(existing.getId());
        try {
            advertiserMapper.updateById(entity);
        } catch (DuplicateKeyException ex) {
            throw new BusinessException(ErrorCode.DUPLICATE_RESOURCE, "广告主名称已存在");
        }
        return new AdvertiserOperationResponse(request.publicId());
    }

    @Override
    public AdvertiserOperationResponse delete(DeleteAdvertiserRequest request) {
        int deleted = advertiserMapper.delete(new LambdaQueryWrapper<AdvertiserEntity>()
                .eq(AdvertiserEntity::getPublicId, request.publicId()));
        if (deleted == 0) {
            throw new BusinessException(ErrorCode.RESOURCE_NOT_FOUND, "广告主不存在");
        }
        return new AdvertiserOperationResponse(request.publicId());
    }
}
