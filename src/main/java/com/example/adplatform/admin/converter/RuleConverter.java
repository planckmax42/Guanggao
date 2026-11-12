package com.example.adplatform.admin.converter;

import com.example.adplatform.admin.request.rule.CreateRuleRequest;
import com.example.adplatform.admin.request.rule.UpdateRuleRequest;
import com.example.adplatform.admin.response.rule.RuleOperationResponse;
import com.example.adplatform.admin.response.PageResponse;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.example.adplatform.admin.entity.RuleEntity;
import com.example.adplatform.admin.response.rule.RuleResponse;
import org.mapstruct.Mapper;
import org.mapstruct.Mapping;

@Mapper(componentModel = "spring")
public interface RuleConverter {

    @Mapping(target = "id", ignore = true)
    @Mapping(target = "createdAt", ignore = true)
    @Mapping(target = "updatedAt", ignore = true)
    RuleEntity createRequestToEntity(CreateRuleRequest request);

    @Mapping(target = "advertiserId", ignore = true)
    @Mapping(target = "id", ignore = true)
    @Mapping(target = "createdAt", ignore = true)
    @Mapping(target = "updatedAt", ignore = true)
    RuleEntity updateRequestToEntity(UpdateRuleRequest request);

    RuleOperationResponse entityToOperationResponse(RuleEntity entity);

    @Mapping(target = "deviceTypes", defaultExpression = "java(java.util.List.of())")
    RuleResponse entityToQueryResponse(RuleEntity entity);

    PageResponse<RuleResponse> pageToResponse(Page<RuleEntity> page);
}
