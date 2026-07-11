package com.example.adplatform.admin.converter;

import com.example.adplatform.admin.dto.CreateMaterialRequest;
import com.example.adplatform.admin.entity.MaterialAuditStatus;
import com.example.adplatform.admin.entity.MaterialEntity;
import com.example.adplatform.admin.vo.MaterialVO;
import com.example.adplatform.common.enums.CommonStatus;
import com.example.adplatform.common.response.ResourceRefVO;
import org.mapstruct.Mapper;
import org.mapstruct.Mapping;

@Mapper(componentModel = "spring", imports = {CommonStatus.class, MaterialAuditStatus.class})
public interface MaterialConverter {

    @Mapping(target = "id", ignore = true)
    @Mapping(target = "auditStatus", expression = "java(MaterialAuditStatus.PENDING.name())")
    @Mapping(target = "status", expression = "java(CommonStatus.ENABLED)")
    @Mapping(target = "createdAt", ignore = true)
    @Mapping(target = "updatedAt", ignore = true)
    MaterialEntity toEntity(CreateMaterialRequest request);

    MaterialVO toVO(MaterialEntity entity);

    @Mapping(target = "bizKey", source = "title")
    ResourceRefVO toRef(MaterialEntity entity);
}
