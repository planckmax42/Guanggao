package com.example.adplatform.admin.converter;

import com.example.adplatform.admin.request.rule.CreateRuleRequest;
import com.example.adplatform.admin.request.rule.UpdateRuleRequest;
import com.example.adplatform.admin.response.rule.RuleOperationResponse;
import com.example.adplatform.admin.response.PageResponse;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.example.adplatform.admin.entity.RuleEntity;
import com.example.adplatform.admin.response.rule.RuleResponse;
import org.mapstruct.Mapper;
import org.mapstruct.BeanMapping;
import org.mapstruct.NullValuePropertyMappingStrategy;
import org.mapstruct.Mapping;
import org.mapstruct.MappingTarget;

@Mapper(componentModel = "spring", uses = RuleJsonConverter.class)
public interface RuleConverter {

    @Mapping(target = "id", ignore = true)
    @Mapping(target = "publicId", ignore = true)
    @Mapping(target = "region", source = "regions", qualifiedByName = "toJson")
    @Mapping(target = "deviceType", source = "deviceTypes", qualifiedByName = "toJson")
    @Mapping(target = "userTags", source = "userTags", qualifiedByName = "toJson")
    @Mapping(target = "createdAt", ignore = true)
    @Mapping(target = "updatedAt", ignore = true)
    RuleEntity createRequestToEntity(CreateRuleRequest request);

    @BeanMapping(nullValuePropertyMappingStrategy = NullValuePropertyMappingStrategy.IGNORE)
    @Mapping(target = "id", ignore = true)
    @Mapping(target = "publicId", ignore = true)
    @Mapping(target = "region", source = "regions", qualifiedByName = "toJson")
    @Mapping(target = "deviceType", source = "deviceTypes", qualifiedByName = "toJson")
    @Mapping(target = "userTags", source = "userTags", qualifiedByName = "toJson")
    @Mapping(target = "createdAt", ignore = true)
    @Mapping(target = "updatedAt", ignore = true)
    void updateRequestToEntity(UpdateRuleRequest request, @MappingTarget RuleEntity entity);

    RuleOperationResponse entityToOperationResponse(RuleEntity entity);

    @Mapping(target = "regions", source = "entity.region", qualifiedByName = "fromJson")
    @Mapping(target = "deviceTypes", source = "entity.deviceType", qualifiedByName = "fromJson")
    @Mapping(target = "userTags", source = "entity.userTags", qualifiedByName = "fromJson")
    RuleResponse entityToQueryResponse(RuleEntity entity);

    PageResponse<RuleResponse> pageToResponse(Page<RuleEntity> page);
}
