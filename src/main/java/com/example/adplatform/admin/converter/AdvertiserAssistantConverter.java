package com.example.adplatform.admin.converter;

import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.example.adplatform.admin.entity.AdvertiserAssistantEntity;
import com.example.adplatform.admin.request.advertiserassistant.CreateAdvertiserAssistantRequest;
import com.example.adplatform.admin.request.advertiserassistant.UpdateAdvertiserAssistantRequest;
import com.example.adplatform.admin.response.PageResponse;
import com.example.adplatform.admin.response.advertiserassistant.AdvertiserAssistantOperationResponse;
import com.example.adplatform.admin.response.advertiserassistant.AdvertiserAssistantQueryResponse;
import com.example.adplatform.common.enums.CommonStatus;
import org.mapstruct.Mapper;
import org.mapstruct.Mapping;

@Mapper(componentModel = "spring", imports = CommonStatus.class)
public interface AdvertiserAssistantConverter {

    @Mapping(target = "id", ignore = true)
    @Mapping(target = "status", expression = "java(CommonStatus.ENABLED)")
    @Mapping(target = "createdAt", ignore = true)
    @Mapping(target = "updatedAt", ignore = true)
    AdvertiserAssistantEntity createRequestToEntity(CreateAdvertiserAssistantRequest request);

    @Mapping(target = "id", ignore = true)
    @Mapping(target = "advertiserId", ignore = true)
    @Mapping(target = "status", ignore = true)
    @Mapping(target = "createdAt", ignore = true)
    @Mapping(target = "updatedAt", ignore = true)
    AdvertiserAssistantEntity updateRequestToEntity(UpdateAdvertiserAssistantRequest request);

    AdvertiserAssistantOperationResponse entityToOperationResponse(AdvertiserAssistantEntity entity);

    AdvertiserAssistantQueryResponse entityToQueryResponse(AdvertiserAssistantEntity entity);

    PageResponse<AdvertiserAssistantQueryResponse> pageToResponse(Page<AdvertiserAssistantEntity> page);
}
