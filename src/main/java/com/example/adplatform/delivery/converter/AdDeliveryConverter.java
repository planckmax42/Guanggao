package com.example.adplatform.delivery.converter;

import com.example.adplatform.admin.entity.PlanEntity;
import com.example.adplatform.admin.entity.MaterialEntity;
import com.example.adplatform.delivery.response.AdItemResponse;
import org.mapstruct.Mapper;
import org.mapstruct.Mapping;

@Mapper(componentModel = "spring")
public interface AdDeliveryConverter {

    @Mapping(target = "planPublicId", source = "plan.publicId")
    @Mapping(target = "materialPublicId", source = "material.publicId")
    @Mapping(target = "slotPublicId", ignore = true)
    @Mapping(target = "title", source = "material.title")
    @Mapping(target = "description", source = "material.description")
    @Mapping(target = "imageUrl", source = "material.imageUrl")
    @Mapping(target = "landingPageUrl", source = "material.landingPageUrl")
    @Mapping(target = "bidPrice", source = "plan.bidPrice")
    AdItemResponse toAdItemResponse(MaterialEntity material, PlanEntity plan, double score);
}
