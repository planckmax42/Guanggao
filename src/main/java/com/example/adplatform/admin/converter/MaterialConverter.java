package com.example.adplatform.admin.converter;

import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.example.adplatform.admin.request.material.CreateMaterialRequest;
import com.example.adplatform.admin.entity.MaterialAuditStatus;
import com.example.adplatform.admin.entity.MaterialEntity;
import com.example.adplatform.admin.response.PageResponse;
import com.example.adplatform.admin.response.material.MaterialOperationResponse;
import com.example.adplatform.admin.response.material.MaterialQueryResponse;
import com.example.adplatform.common.enums.CommonStatus;
import org.mapstruct.Mapper;
import org.mapstruct.Mapping;

@Mapper(componentModel = "spring", imports = {CommonStatus.class, MaterialAuditStatus.class})
public interface MaterialConverter {

    @Mapping(target = "id", ignore = true)
    @Mapping(target = "publicId", ignore = true)
    @Mapping(target = "status", expression = "java(MaterialAuditStatus.PENDING.name())")
    @Mapping(target = "status", expression = "java(CommonStatus.ENABLED)")
    @Mapping(target = "createdAt", ignore = true)
    @Mapping(target = "updatedAt", ignore = true)
    MaterialEntity requestToEntity(CreateMaterialRequest request);


    MaterialQueryResponse entityToQueryResponse(MaterialEntity entity);

    MaterialOperationResponse entityToOperationResponse(MaterialEntity entity);

    PageResponse<MaterialQueryResponse>pageToResponse(Page<MaterialEntity> page);
}
