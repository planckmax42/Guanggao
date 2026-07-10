package com.example.adplatform.admin.converter;

import com.example.adplatform.admin.dto.CreateCreativeRequest;
import com.example.adplatform.admin.entity.CreativeAuditStatus;
import com.example.adplatform.admin.entity.CreativeEntity;
import com.example.adplatform.admin.vo.CreativeVO;
import com.example.adplatform.common.enums.CommonStatus;
import com.example.adplatform.common.response.ResourceRefVO;
import org.mapstruct.Mapper;
import org.mapstruct.Mapping;

@Mapper(componentModel = "spring", imports = {CommonStatus.class, CreativeAuditStatus.class})
public interface CreativeConverter {

    @Mapping(target = "id", ignore = true)
    @Mapping(target = "auditStatus", expression = "java(CreativeAuditStatus.PENDING.name())")
    @Mapping(target = "status", expression = "java(CommonStatus.ENABLED)")
    @Mapping(target = "createdAt", ignore = true)
    @Mapping(target = "updatedAt", ignore = true)
    CreativeEntity toEntity(CreateCreativeRequest request);

    CreativeVO toVO(CreativeEntity entity);

    @Mapping(target = "bizKey", source = "title")
    ResourceRefVO toRef(CreativeEntity entity);
}
