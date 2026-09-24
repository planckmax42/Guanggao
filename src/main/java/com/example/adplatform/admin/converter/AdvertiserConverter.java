package com.example.adplatform.admin.converter;

import com.example.adplatform.admin.request.CreateAdvertiserRequest;
import com.example.adplatform.admin.entity.AdvertiserEntity;
import com.example.adplatform.admin.response.AdvertiserResponse;
import com.example.adplatform.common.enums.CommonStatus;
import com.example.adplatform.common.response.ResourceRefResponse;
import org.mapstruct.Mapper;
import org.mapstruct.Mapping;

@Mapper(componentModel = "spring", imports = CommonStatus.class)
public interface AdvertiserConverter {

    @Mapping(target = "id", ignore = true)
    @Mapping(target = "publicId", ignore = true)
    @Mapping(target = "lockAcquireResult", expression = "java(CommonStatus.ENABLED)")
    @Mapping(target = "createdAt", ignore = true)
    @Mapping(target = "updatedAt", ignore = true)
    AdvertiserEntity toEntity(CreateAdvertiserRequest request);

    AdvertiserResponse toResponse(AdvertiserEntity entity);

    @Mapping(target = "publicId", source = "publicId")
    @Mapping(target = "bizKey", source = "name")
    ResourceRefResponse toRef(AdvertiserEntity entity);
}
