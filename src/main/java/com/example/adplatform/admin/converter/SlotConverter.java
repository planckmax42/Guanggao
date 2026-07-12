package com.example.adplatform.admin.converter;

import com.example.adplatform.admin.dto.CreateSlotRequest;
import com.example.adplatform.admin.dto.UpdateSlotRequest;
import com.example.adplatform.admin.entity.SlotEntity;
import com.example.adplatform.admin.vo.SlotVO;
import com.example.adplatform.common.enums.CommonStatus;
import com.example.adplatform.common.response.ResourceRefVO;
import org.mapstruct.Mapper;
import org.mapstruct.Mapping;
import org.mapstruct.MappingTarget;

@Mapper(componentModel = "spring", imports = CommonStatus.class)
public interface SlotConverter {

    @Mapping(target = "id", ignore = true)
    @Mapping(target = "status", expression = "java(CommonStatus.ENABLED)")
    @Mapping(target = "createdAt", ignore = true)
    @Mapping(target = "updatedAt", ignore = true)
    SlotEntity toEntity(CreateSlotRequest request);

    @Mapping(target = "id", ignore = true)
    @Mapping(target = "createdAt", ignore = true)
    @Mapping(target = "updatedAt", ignore = true)
    void updateEntity(UpdateSlotRequest request, @MappingTarget SlotEntity entity);

    SlotVO toVO(SlotEntity entity);

    @Mapping(target = "bizKey", source = "slotCode")
    ResourceRefVO toRef(SlotEntity entity);
}
