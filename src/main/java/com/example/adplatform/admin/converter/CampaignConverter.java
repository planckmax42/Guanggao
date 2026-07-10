package com.example.adplatform.admin.converter;

import com.example.adplatform.admin.dto.CreateCampaignRequest;
import com.example.adplatform.admin.dto.UpdateCampaignRequest;
import com.example.adplatform.admin.entity.BillingType;
import com.example.adplatform.admin.entity.CampaignEntity;
import com.example.adplatform.admin.entity.CampaignStatus;
import com.example.adplatform.admin.vo.CampaignVO;
import com.example.adplatform.common.response.ResourceRefVO;
import org.mapstruct.Mapper;
import org.mapstruct.Mapping;
import org.mapstruct.MappingTarget;

@Mapper(componentModel = "spring", imports = {BillingType.class, CampaignStatus.class})
public interface CampaignConverter {

    @Mapping(target = "id", ignore = true)
    @Mapping(target = "billingType", expression = "java(BillingType.normalizeOrDefault(request.billingType()))")
    @Mapping(target = "status", expression = "java(CampaignStatus.DRAFT.name())")
    @Mapping(target = "createdAt", ignore = true)
    @Mapping(target = "updatedAt", ignore = true)
    CampaignEntity toEntity(CreateCampaignRequest request);

    @Mapping(target = "id", ignore = true)
    @Mapping(target = "advertiserId", ignore = true)
    @Mapping(target = "billingType", expression = "java(BillingType.normalizeOrDefault(request.billingType()))")
    @Mapping(target = "status", ignore = true)
    @Mapping(target = "createdAt", ignore = true)
    @Mapping(target = "updatedAt", ignore = true)
    void updateEntity(UpdateCampaignRequest request, @MappingTarget CampaignEntity entity);

    CampaignVO toVO(CampaignEntity entity);

    @Mapping(target = "bizKey", source = "name")
    ResourceRefVO toRef(CampaignEntity entity);
}
