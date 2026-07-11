package com.example.adplatform.delivery.converter;

import com.example.adplatform.admin.entity.PlanEntity;
import com.example.adplatform.admin.entity.MaterialEntity;
import com.example.adplatform.delivery.vo.AdItemVO;
import org.mapstruct.Mapper;
import org.mapstruct.Mapping;

@Mapper(componentModel = "spring")
public interface AdDeliveryConverter {

    @Mapping(target = "planId", source = "plan.id")
    @Mapping(target = "materialId", source = "material.id")
    @Mapping(target = "slotId", source = "material.slotId")
    @Mapping(target = "title", source = "material.title")
    @Mapping(target = "description", source = "material.description")
    @Mapping(target = "imageUrl", source = "material.imageUrl")
    @Mapping(target = "landingPageUrl", source = "material.landingPageUrl")
    @Mapping(target = "bidPrice", source = "plan.bidPrice")
    AdItemVO toAdItemVO(MaterialEntity material, PlanEntity plan, double score);
}
