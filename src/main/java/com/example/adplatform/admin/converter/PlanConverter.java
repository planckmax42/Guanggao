package com.example.adplatform.admin.converter;

import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.example.adplatform.admin.response.PageResponse;
import com.example.adplatform.admin.request.plan.CreatePlanRequest;
import com.example.adplatform.admin.request.plan.UpdatePlanRequest;
import com.example.adplatform.admin.entity.BillingType;
import com.example.adplatform.admin.entity.PlanEntity;
import com.example.adplatform.admin.entity.PlanStatus;
import com.example.adplatform.admin.response.plan.PlanOperationResponse;
import com.example.adplatform.admin.response.plan.PlanResponse;
import org.mapstruct.Mapper;
import org.mapstruct.Mapping;
import org.mapstruct.MappingTarget;

@Mapper(componentModel = "spring", imports = {BillingType.class, PlanStatus.class})
public interface PlanConverter {

    @Mapping(target = "id", ignore = true)
    @Mapping(target = "publicId", ignore = true)
    @Mapping(target = "createdAt", ignore = true)
    @Mapping(target = "updatedAt", ignore = true)
    @Mapping(target = "status", expression = "java(PlanStatus.DRAFT.name())")
    @Mapping(target = "billingType", expression = "java(BillingType.normalizeOrDefault(request.billingType()))")
    PlanEntity requestToEntity(CreatePlanRequest request);

    PlanResponse entityToQueryResponse(PlanEntity entity);

    PageResponse<PlanResponse> pageToResponse(Page<PlanEntity> page);

    @Mapping(target = "id", ignore = true)
    @Mapping(target = "publicId", ignore = true)
    @Mapping(target = "advertiserPublicId", ignore = true)
    @Mapping(target = "billingType", expression = "java(BillingType.normalizeOrDefault(request.billingType()))")
    @Mapping(target = "status", ignore = true)
    @Mapping(target = "createdAt", ignore = true)
    @Mapping(target = "updatedAt", ignore = true)
    void requestToEntity(UpdatePlanRequest request, @MappingTarget PlanEntity entity);

    @Mapping(target = "planPublicId", source = "publicId")
    PlanOperationResponse entityToOperationResponse(PlanEntity entity);
}
