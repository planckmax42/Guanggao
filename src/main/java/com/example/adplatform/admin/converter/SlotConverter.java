package com.example.adplatform.admin.converter;

import com.example.adplatform.admin.request.CreateSlotRequest;
import com.example.adplatform.admin.request.UpdateSlotRequest;
import com.example.adplatform.admin.entity.SlotEntity;
import com.example.adplatform.admin.response.AvailableSlotResponse;
import com.example.adplatform.admin.response.SlotResponse;
import com.example.adplatform.common.enums.CommonStatus;
import com.example.adplatform.common.response.ResourceRefResponse;
import org.mapstruct.*;

@Mapper(componentModel = "spring", imports = CommonStatus.class)
public interface SlotConverter {

    @Mapping(target = "id", ignore = true)
    @Mapping(target = "publicId", ignore = true)
    @Mapping(target = "createdAt", ignore = true)
    @Mapping(target = "updatedAt", ignore = true)
    SlotEntity toCreateEntity(CreateSlotRequest request);

    @BeanMapping(nullValuePropertyMappingStrategy = NullValuePropertyMappingStrategy.IGNORE)
    @Mapping(target = "id", ignore = true)
    @Mapping(target = "publicId", ignore = true)
    @Mapping(target = "status", ignore = true)
    @Mapping(target = "createdAt", ignore = true)
    @Mapping(target = "updatedAt", ignore = true)
    SlotEntity toUpdateEntity(UpdateSlotRequest request, @MappingTarget SlotEntity entity);

    SlotResponse toResponse(SlotEntity entity);

    AvailableSlotResponse toAvailableResponse(SlotEntity entity);
}
