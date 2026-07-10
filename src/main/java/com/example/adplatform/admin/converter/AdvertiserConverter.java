package com.example.adplatform.admin.converter;

import com.example.adplatform.admin.dto.CreateAdvertiserRequest;
import com.example.adplatform.admin.entity.AdvertiserEntity;
import com.example.adplatform.admin.vo.AdvertiserVO;
import com.example.adplatform.common.enums.CommonStatus;
import com.example.adplatform.common.response.ResourceRefVO;
import org.mapstruct.Mapper;
import org.mapstruct.Mapping;

@Mapper(componentModel = "spring", imports = CommonStatus.class)
public interface AdvertiserConverter {

    @Mapping(target = "id", ignore = true)
    @Mapping(target = "status", expression = "java(CommonStatus.ENABLED)")
    @Mapping(target = "createdAt", ignore = true)
    @Mapping(target = "updatedAt", ignore = true)
    AdvertiserEntity toEntity(CreateAdvertiserRequest request);

    AdvertiserVO toVO(AdvertiserEntity entity);

    @Mapping(target = "bizKey", source = "name")
    ResourceRefVO toRef(AdvertiserEntity entity);
}
