package com.example.adplatform.admin.converter;

import com.example.adplatform.admin.dto.CreatePlanRequest;
import com.example.adplatform.admin.dto.UpdatePlanRequest;
import com.example.adplatform.admin.entity.BillingType;
import com.example.adplatform.admin.entity.PlanEntity;
import com.example.adplatform.admin.entity.PlanStatus;
import com.example.adplatform.admin.vo.PlanVO;
import com.example.adplatform.common.response.ResourceRefVO;
import org.mapstruct.Mapper;
import org.mapstruct.Mapping;
import org.mapstruct.MappingTarget;

@Mapper(componentModel = "spring", imports = {BillingType.class, PlanStatus.class})
public interface PlanConverter {

    @Mapping(target = "id", ignore = true)
    @Mapping(target = "billingType", expression = "java(BillingType.normalizeOrDefault(request.billingType()))")
    @Mapping(target = "status", expression = "java(PlanStatus.DRAFT.name())")
    @Mapping(target = "createdAt", ignore = true)
    @Mapping(target = "updatedAt", ignore = true)
    PlanEntity toEntity(CreatePlanRequest request);

    @Mapping(target = "id", ignore = true)
    @Mapping(target = "userId", ignore = true)
    @Mapping(target = "billingType", expression = "java(BillingType.normalizeOrDefault(request.billingType()))")
    @Mapping(target = "status", ignore = true)
    @Mapping(target = "createdAt", ignore = true)
    @Mapping(target = "updatedAt", ignore = true)
    void updateEntity(UpdatePlanRequest request, @MappingTarget PlanEntity entity);

    PlanVO toVO(PlanEntity entity);

    @Mapping(target = "bizKey", source = "name")
    ResourceRefVO toRef(PlanEntity entity);
}
