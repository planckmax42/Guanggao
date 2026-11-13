package com.example.adplatform.admin.converter;

import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.example.adplatform.admin.entity.AdvertiserWalletEntity;
import com.example.adplatform.admin.request.advertiserwallet.CreateAdvertiserWalletRequest;
import com.example.adplatform.admin.request.advertiserwallet.UpdateAdvertiserWalletRequest;
import com.example.adplatform.admin.response.PageResponse;
import com.example.adplatform.admin.response.advertiserwallet.AdvertiserWalletOperationResponse;
import com.example.adplatform.admin.response.advertiserwallet.AdvertiserWalletQueryResponse;
import org.mapstruct.Mapper;
import org.mapstruct.Mapping;

@Mapper(componentModel = "spring")
public interface AdvertiserWalletConverter {

    @Mapping(target = "id", ignore = true)
    @Mapping(target = "balanceMicro", expression = "java(0L)")
    @Mapping(target = "frozenMicro", expression = "java(0L)")
    @Mapping(target = "updatedAt", ignore = true)
    AdvertiserWalletEntity createRequestToEntity(CreateAdvertiserWalletRequest request);

    @Mapping(target = "id", ignore = true)
    @Mapping(target = "advertiserId", ignore = true)
    @Mapping(target = "updatedAt", ignore = true)
    AdvertiserWalletEntity updateRequestToEntity(UpdateAdvertiserWalletRequest request);

    AdvertiserWalletOperationResponse entityToOperationResponse(AdvertiserWalletEntity entity);

    AdvertiserWalletQueryResponse entityToQueryResponse(AdvertiserWalletEntity entity);

    PageResponse<AdvertiserWalletQueryResponse> pageToResponse(Page<AdvertiserWalletEntity> page);
}
