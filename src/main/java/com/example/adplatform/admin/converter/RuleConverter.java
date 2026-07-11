package com.example.adplatform.admin.converter;

import com.example.adplatform.admin.dto.CreateRuleRequest;
import com.example.adplatform.admin.entity.RuleEntity;
import com.example.adplatform.admin.vo.RuleVO;
import com.example.adplatform.common.exception.BusinessException;
import com.example.adplatform.common.exception.ErrorCode;
import com.example.adplatform.common.response.ResourceRefVO;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.mapstruct.Mapper;
import org.mapstruct.Mapping;
import org.mapstruct.MappingTarget;
import org.mapstruct.Named;
import org.springframework.beans.factory.annotation.Autowired;

import java.util.List;

@Mapper(componentModel = "spring")
public abstract class RuleConverter {

    private static final TypeReference<List<String>> STRING_LIST_TYPE = new TypeReference<>() {
    };

    @Autowired
    private ObjectMapper objectMapper;

    @Mapping(target = "id", ignore = true)
    @Mapping(target = "region", source = "regions", qualifiedByName = "toJson")
    @Mapping(target = "deviceType", source = "deviceTypes", qualifiedByName = "toJson")
    @Mapping(target = "userTags", source = "userTags", qualifiedByName = "toJson")
    @Mapping(target = "createdAt", ignore = true)
    @Mapping(target = "updatedAt", ignore = true)
    public abstract RuleEntity toEntity(CreateRuleRequest request);

    @Mapping(target = "id", ignore = true)
    @Mapping(target = "planId", ignore = true)
    @Mapping(target = "region", source = "regions", qualifiedByName = "toJson")
    @Mapping(target = "deviceType", source = "deviceTypes", qualifiedByName = "toJson")
    @Mapping(target = "userTags", source = "userTags", qualifiedByName = "toJson")
    @Mapping(target = "createdAt", ignore = true)
    @Mapping(target = "updatedAt", ignore = true)
    public abstract void updateEntity(CreateRuleRequest request, @MappingTarget RuleEntity entity);

    @Mapping(target = "regions", source = "region", qualifiedByName = "fromJson")
    @Mapping(target = "deviceTypes", source = "deviceType", qualifiedByName = "fromJson")
    @Mapping(target = "userTags", source = "userTags", qualifiedByName = "fromJson")
    public abstract RuleVO toVO(RuleEntity entity);

    @Mapping(target = "bizKey", expression = "java(String.valueOf(entity.getPlanId()))")
    public abstract ResourceRefVO toRef(RuleEntity entity);

    @Named("toJson")
    protected String toJson(List<String> values) {
        if (values == null || values.isEmpty()) {
            return null;
        }
        try {
            return objectMapper.writeValueAsString(values);
        } catch (JsonProcessingException ex) {
            throw new BusinessException(ErrorCode.BUSINESS_ERROR, "定向规则序列化失败");
        }
    }

    @Named("fromJson")
    protected List<String> fromJson(String value) {
        if (value == null || value.isBlank()) {
            return List.of();
        }
        try {
            return objectMapper.readValue(value, STRING_LIST_TYPE);
        } catch (JsonProcessingException ex) {
            throw new BusinessException(ErrorCode.BUSINESS_ERROR, "定向规则解析失败");
        }
    }
}
