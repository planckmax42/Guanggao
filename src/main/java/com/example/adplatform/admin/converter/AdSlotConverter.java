package com.example.adplatform.admin.converter;

import com.example.adplatform.admin.dto.CreateAdSlotRequest;
import com.example.adplatform.admin.entity.AdSlotEntity;
import com.example.adplatform.admin.vo.AdSlotVO;
import com.example.adplatform.common.enums.CommonStatus;
import com.example.adplatform.common.response.ResourceRefVO;
import org.mapstruct.Mapper;
import org.mapstruct.Mapping;

@Mapper(componentModel = "spring", imports = CommonStatus.class)
public interface AdSlotConverter {

    @Mapping(target = "id", ignore = true)
    @Mapping(target = "status", expression = "java(CommonStatus.ENABLED)")
    @Mapping(target = "createdAt", ignore = true)
    @Mapping(target = "updatedAt", ignore = true)
    AdSlotEntity toEntity(CreateAdSlotRequest request);

    AdSlotVO toVO(AdSlotEntity entity);

    @Mapping(target = "bizKey", source = "slotCode")
    ResourceRefVO toRef(AdSlotEntity entity);
}
