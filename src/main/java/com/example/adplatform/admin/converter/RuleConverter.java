package com.example.adplatform.admin.converter;

import com.example.adplatform.admin.request.rule.CreateRuleRequest;
import com.example.adplatform.admin.entity.RuleEntity;
import com.example.adplatform.admin.response.rule.RuleResponse;
import com.example.adplatform.common.response.ResourceRefResponse;
import org.mapstruct.Mapper;
import org.mapstruct.Mapping;
import org.mapstruct.MappingTarget;

@Mapper(componentModel = "spring", uses = RuleJsonConverter.class)
public interface RuleConverter {

    @Mapping(target = "id", ignore = true)
    @Mapping(target = "publicId", ignore = true)
    @Mapping(target = "planId", ignore = true)
    @Mapping(target = "region", source = "regions", qualifiedByName = "toJson")
    @Mapping(target = "deviceType", source = "deviceTypes", qualifiedByName = "toJson")
    @Mapping(target = "userTags", source = "userTags", qualifiedByName = "toJson")
    @Mapping(target = "createdAt", ignore = true)
    @Mapping(target = "updatedAt", ignore = true)
    RuleEntity toEntity(CreateRuleRequest request);

    @Mapping(target = "id", ignore = true)
    @Mapping(target = "publicId", ignore = true)
    @Mapping(target = "planId", ignore = true)
    @Mapping(target = "region", source = "regions", qualifiedByName = "toJson")
    @Mapping(target = "deviceType", source = "deviceTypes", qualifiedByName = "toJson")
    @Mapping(target = "userTags", source = "userTags", qualifiedByName = "toJson")
    @Mapping(target = "createdAt", ignore = true)
    @Mapping(target = "updatedAt", ignore = true)
    void updateEntity(CreateRuleRequest request, @MappingTarget RuleEntity entity);

    @Mapping(target = "regions", source = "entity.region", qualifiedByName = "fromJson")
    @Mapping(target = "deviceTypes", source = "entity.deviceType", qualifiedByName = "fromJson")
    @Mapping(target = "userTags", source = "entity.userTags", qualifiedByName = "fromJson")
    @Mapping(target = "planPublicId", source = "planPublicId")
    RuleResponse toResponse(RuleEntity entity, String planPublicId);

    @Mapping(target = "publicId", source = "entity.publicId")
    @Mapping(target = "bizKey", source = "planPublicId")
    ResourceRefResponse toRef(RuleEntity entity, String planPublicId);
}
