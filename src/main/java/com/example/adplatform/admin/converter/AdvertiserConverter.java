package com.example.adplatform.admin.converter;

import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.example.adplatform.admin.request.advertiser.CreateAdvertiserRequest;
import com.example.adplatform.admin.request.advertiser.UpdateAdvertiserRequest;
import com.example.adplatform.admin.entity.AdvertiserEntity;
import com.example.adplatform.admin.response.PageResponse;
import com.example.adplatform.admin.response.advertiser.AdvertiserQueryResponse;
import com.example.adplatform.admin.response.advertiser.AdvertiserOperationResponse;
import com.example.adplatform.common.enums.CommonStatus;
import org.mapstruct.Mapper;
import org.mapstruct.Mapping;

@Mapper(componentModel = "spring", imports = CommonStatus.class)
public interface AdvertiserConverter {

    @Mapping(target = "id", ignore = true)
    @Mapping(target = "status", expression = "java(CommonStatus.ENABLED)")
    @Mapping(target = "createdAt", ignore = true)
    @Mapping(target = "updatedAt", ignore = true)
    AdvertiserEntity createRequestToEntity(CreateAdvertiserRequest request);

    @Mapping(target = "id", ignore = true)
    @Mapping(target = "status", ignore = true)
    @Mapping(target = "createdAt", ignore = true)
    @Mapping(target = "updatedAt", ignore = true)
    AdvertiserEntity updateRequestToEntity(UpdateAdvertiserRequest request);

    AdvertiserOperationResponse entityToOperationResponse(AdvertiserEntity entity);

    AdvertiserQueryResponse entityToQueryResponse(AdvertiserEntity entity);

    PageResponse<AdvertiserQueryResponse> pageToResponse(Page<AdvertiserEntity> page);
}
