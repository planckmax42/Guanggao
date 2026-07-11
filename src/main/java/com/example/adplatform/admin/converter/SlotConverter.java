package com.example.adplatform.admin.converter;

import com.example.adplatform.admin.dto.CreateSlotRequest;
import com.example.adplatform.admin.entity.SlotEntity;
import com.example.adplatform.admin.vo.SlotVO;
import com.example.adplatform.common.enums.CommonStatus;
import com.example.adplatform.common.response.ResourceRefVO;
import org.mapstruct.Mapper;
import org.mapstruct.Mapping;

@Mapper(componentModel = "spring", imports = CommonStatus.class)
public interface SlotConverter {

    @Mapping(target = "id", ignore = true)
    @Mapping(target = "status", expression = "java(CommonStatus.ENABLED)")
    @Mapping(target = "createdAt", ignore = true)
    @Mapping(target = "updatedAt", ignore = true)
    SlotEntity toEntity(CreateSlotRequest request);

    SlotVO toVO(SlotEntity entity);

    @Mapping(target = "bizKey", source = "slotCode")
    ResourceRefVO toRef(SlotEntity entity);
}
