package com.example.adplatform.admin.converter;

import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.example.adplatform.admin.response.PageResponse;
import com.example.adplatform.admin.request.slot.CreateSlotRequest;
import com.example.adplatform.admin.request.slot.UpdateSlotRequest;
import com.example.adplatform.admin.entity.SlotEntity;
import com.example.adplatform.admin.response.slot.AvailableSlotResponse;
import com.example.adplatform.admin.response.slot.SlotQueryResponse;
import com.example.adplatform.admin.response.slot.SlotOperationResponse;
import com.example.adplatform.common.enums.CommonStatus;
import org.mapstruct.*;

@Mapper(componentModel = "spring", imports = CommonStatus.class)
public interface SlotConverter {

    @Mapping(target = "id", ignore = true)
    @Mapping(target = "publicId", ignore = true)
    @Mapping(target = "createdAt", ignore = true)
    @Mapping(target = "updatedAt", ignore = true)
    SlotEntity createRequestToEntity(CreateSlotRequest request);

    @BeanMapping(nullValuePropertyMappingStrategy = NullValuePropertyMappingStrategy.IGNORE)
    @Mapping(target = "id", ignore = true)
    @Mapping(target = "publicId", ignore = true)
    @Mapping(target = "status", ignore = true)
    @Mapping(target = "createdAt", ignore = true)
    @Mapping(target = "updatedAt", ignore = true)
    SlotEntity updateRequestToEntity(UpdateSlotRequest request, @MappingTarget SlotEntity entity);

    SlotOperationResponse entityToOperationResponse(SlotEntity entity);

    SlotQueryResponse entityToQueryResponse(SlotEntity entity);

    PageResponse<SlotQueryResponse> pageToResponse(Page<SlotEntity> page);

    AvailableSlotResponse entityToAvailableResponse(SlotEntity entity);
}
