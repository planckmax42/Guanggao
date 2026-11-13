package com.example.adplatform.admin.service.impl;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.example.adplatform.admin.converter.AdvertiserAssistantConverter;
import com.example.adplatform.admin.entity.AdvertiserAssistantEntity;
import com.example.adplatform.admin.mapper.AdvertiserAssistantMapper;
import com.example.adplatform.admin.request.advertiserassistant.CreateAdvertiserAssistantRequest;
import com.example.adplatform.admin.request.advertiserassistant.PageAdvertiserAssistantRequest;
import com.example.adplatform.admin.request.advertiserassistant.UpdateAdvertiserAssistantRequest;
import com.example.adplatform.admin.request.advertiserassistant.DeleteAdvertiserAssistantRequest;
import com.example.adplatform.admin.response.PageResponse;
import com.example.adplatform.admin.response.advertiserassistant.AdvertiserAssistantOperationResponse;
import com.example.adplatform.admin.response.advertiserassistant.AdvertiserAssistantQueryResponse;
import com.example.adplatform.admin.service.AdvertiserAssistantService;
import com.example.adplatform.admin.util.AdvertiserGuardUtils;
import com.example.adplatform.common.exception.BusinessException;
import com.example.adplatform.common.exception.ErrorCode;
import com.example.adplatform.common.util.MySqlForeignKeyUtils;
import lombok.RequiredArgsConstructor;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.dao.DuplicateKeyException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@RequiredArgsConstructor
@Service
public class AdvertiserAssistantServiceImpl implements AdvertiserAssistantService {

    private final AdvertiserAssistantMapper advertiserAssistantMapper;
    private final AdvertiserAssistantConverter advertiserAssistantConverter;
    private final AdvertiserGuardUtils advertiserGuardUtils;

    @Override
    @Transactional(rollbackFor = Exception.class)
    public AdvertiserAssistantOperationResponse create(CreateAdvertiserAssistantRequest request) {
        advertiserGuardUtils.requireEnabled(request.advertiserId());
        AdvertiserAssistantEntity entity = advertiserAssistantConverter.createRequestToEntity(request);
        try {
            advertiserAssistantMapper.insert(entity);
        } catch (DuplicateKeyException ex) {
            throw new BusinessException(ErrorCode.DUPLICATE_RESOURCE, "副账号用户名已存在");
        } catch (DataIntegrityViolationException ex) {
            if (MySqlForeignKeyUtils.isReferencedRowMissing(ex)) {
                throw new BusinessException(ErrorCode.RESOURCE_NOT_FOUND, "广告主不存在", ex);
            }
            throw ex;
        }
        return advertiserAssistantConverter.entityToOperationResponse(entity);
    }

    @Override
    public PageResponse<AdvertiserAssistantQueryResponse> pageQuery(PageAdvertiserAssistantRequest request) {
        Page<AdvertiserAssistantEntity> page = new Page<>(request.current(), request.size());
        Page<AdvertiserAssistantEntity> result = advertiserAssistantMapper.selectPage(page,
                new LambdaQueryWrapper<AdvertiserAssistantEntity>().orderByDesc(AdvertiserAssistantEntity::getId));
        return advertiserAssistantConverter.pageToResponse(result);
    }

    @Override
    public AdvertiserAssistantOperationResponse update(UpdateAdvertiserAssistantRequest request) {
        AdvertiserAssistantEntity existing = advertiserAssistantMapper.selectById(request.id());
        if (existing == null) {
            throw new BusinessException(ErrorCode.RESOURCE_NOT_FOUND, "副账号不存在");
        }
        boolean changed = (request.username() != null && !request.username().equals(existing.getUsername()))
                || (request.name() != null && !request.name().equals(existing.getName()))
                || (request.role() != null && !request.role().equals(existing.getRole()));
        if (!changed) {
            return new AdvertiserAssistantOperationResponse(request.id());
        }
        AdvertiserAssistantEntity entity = advertiserAssistantConverter.updateRequestToEntity(request);
        entity.setId(existing.getId());
        try {
            if (advertiserAssistantMapper.updateById(entity) == 0) {
                throw new BusinessException(ErrorCode.RESOURCE_NOT_FOUND, "副账号不存在");
            }
        } catch (DuplicateKeyException ex) {
            throw new BusinessException(ErrorCode.DUPLICATE_RESOURCE, "副账号用户名已存在");
        }
        return new AdvertiserAssistantOperationResponse(request.id());
    }

    @Override
    public AdvertiserAssistantOperationResponse delete(DeleteAdvertiserAssistantRequest request) {
        try {
            if (advertiserAssistantMapper.deleteById(request.id()) == 0) {
                throw new BusinessException(ErrorCode.RESOURCE_NOT_FOUND, "副账号不存在");
            }
        } catch (DataIntegrityViolationException ex) {
            if (MySqlForeignKeyUtils.isReferencedByOtherRows(ex)) {
                throw new BusinessException(ErrorCode.RESOURCE_IN_USE, "副账号存在关联数据，不能删除", ex);
            }
            throw ex;
        }
        return new AdvertiserAssistantOperationResponse(request.id());
    }
}
