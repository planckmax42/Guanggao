package com.example.adplatform.admin.service.impl;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.example.adplatform.admin.converter.AdvertiserWalletConverter;
import com.example.adplatform.admin.entity.AdvertiserWalletEntity;
import com.example.adplatform.admin.mapper.AdvertiserWalletMapper;
import com.example.adplatform.admin.request.advertiserwallet.CreateAdvertiserWalletRequest;
import com.example.adplatform.admin.request.advertiserwallet.PageAdvertiserWalletRequest;
import com.example.adplatform.admin.request.advertiserwallet.UpdateAdvertiserWalletRequest;
import com.example.adplatform.admin.request.advertiserwallet.DeleteAdvertiserWalletRequest;
import com.example.adplatform.admin.response.PageResponse;
import com.example.adplatform.admin.response.advertiserwallet.AdvertiserWalletOperationResponse;
import com.example.adplatform.admin.response.advertiserwallet.AdvertiserWalletQueryResponse;
import com.example.adplatform.admin.service.AdvertiserWalletService;
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
public class AdvertiserWalletServiceImpl implements AdvertiserWalletService {

    private final AdvertiserWalletMapper advertiserWalletMapper;
    private final AdvertiserWalletConverter advertiserWalletConverter;
    private final AdvertiserGuardUtils advertiserGuardUtils;

    @Override
    @Transactional(rollbackFor = Exception.class)
    public AdvertiserWalletOperationResponse create(CreateAdvertiserWalletRequest request) {
        advertiserGuardUtils.requireEnabled(request.advertiserId());
        AdvertiserWalletEntity entity = advertiserWalletConverter.createRequestToEntity(request);
        try {
            advertiserWalletMapper.insert(entity);
        } catch (DuplicateKeyException ex) {
            throw new BusinessException(ErrorCode.DUPLICATE_RESOURCE, "该广告主已存在钱包");
        } catch (DataIntegrityViolationException ex) {
            if (MySqlForeignKeyUtils.isReferencedRowMissing(ex)) {
                throw new BusinessException(ErrorCode.RESOURCE_NOT_FOUND, "广告主不存在", ex);
            }
            throw ex;
        }
        return advertiserWalletConverter.entityToOperationResponse(entity);
    }

    @Override
    public PageResponse<AdvertiserWalletQueryResponse> pageQuery(PageAdvertiserWalletRequest request) {
        Page<AdvertiserWalletEntity> page = new Page<>(request.current(), request.size());
        Page<AdvertiserWalletEntity> result = advertiserWalletMapper.selectPage(page,
                new LambdaQueryWrapper<AdvertiserWalletEntity>().orderByDesc(AdvertiserWalletEntity::getId));
        return advertiserWalletConverter.pageToResponse(result);
    }

    @Override
    public AdvertiserWalletOperationResponse update(UpdateAdvertiserWalletRequest request) {
        AdvertiserWalletEntity existing = advertiserWalletMapper.selectById(request.id());
        if (existing == null) {
            throw new BusinessException(ErrorCode.RESOURCE_NOT_FOUND, "钱包不存在");
        }
        boolean changed = (request.balanceMicro() != null && !request.balanceMicro().equals(existing.getBalanceMicro()))
                || (request.frozenMicro() != null && !request.frozenMicro().equals(existing.getFrozenMicro()));
        if (!changed) {
            return new AdvertiserWalletOperationResponse(request.id());
        }
        AdvertiserWalletEntity entity = advertiserWalletConverter.updateRequestToEntity(request);
        entity.setId(existing.getId());
        if (advertiserWalletMapper.updateById(entity) == 0) {
            throw new BusinessException(ErrorCode.RESOURCE_NOT_FOUND, "钱包不存在");
        }
        return new AdvertiserWalletOperationResponse(request.id());
    }

    @Override
    public AdvertiserWalletOperationResponse delete(DeleteAdvertiserWalletRequest request) {
        try {
            if (advertiserWalletMapper.deleteById(request.id()) == 0) {
                throw new BusinessException(ErrorCode.RESOURCE_NOT_FOUND, "钱包不存在");
            }
        } catch (DataIntegrityViolationException ex) {
            if (MySqlForeignKeyUtils.isReferencedByOtherRows(ex)) {
                throw new BusinessException(ErrorCode.RESOURCE_IN_USE, "钱包存在关联数据，不能删除", ex);
            }
            throw ex;
        }
        return new AdvertiserWalletOperationResponse(request.id());
    }
}
