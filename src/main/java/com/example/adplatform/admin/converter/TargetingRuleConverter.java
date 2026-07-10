package com.example.adplatform.admin.converter;

import com.example.adplatform.admin.dto.CreateTargetingRuleRequest;
import com.example.adplatform.admin.entity.TargetingRuleEntity;
import com.example.adplatform.admin.vo.TargetingRuleVO;
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
public abstract class TargetingRuleConverter {

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
    public abstract TargetingRuleEntity toEntity(CreateTargetingRuleRequest request);

    @Mapping(target = "id", ignore = true)
    @Mapping(target = "campaignId", ignore = true)
    @Mapping(target = "region", source = "regions", qualifiedByName = "toJson")
    @Mapping(target = "deviceType", source = "deviceTypes", qualifiedByName = "toJson")
    @Mapping(target = "userTags", source = "userTags", qualifiedByName = "toJson")
    @Mapping(target = "createdAt", ignore = true)
    @Mapping(target = "updatedAt", ignore = true)
    public abstract void updateEntity(CreateTargetingRuleRequest request, @MappingTarget TargetingRuleEntity entity);

    @Mapping(target = "regions", source = "region", qualifiedByName = "fromJson")
    @Mapping(target = "deviceTypes", source = "deviceType", qualifiedByName = "fromJson")
    @Mapping(target = "userTags", source = "userTags", qualifiedByName = "fromJson")
    public abstract TargetingRuleVO toVO(TargetingRuleEntity entity);

    @Mapping(target = "bizKey", expression = "java(String.valueOf(entity.getCampaignId()))")
    public abstract ResourceRefVO toRef(TargetingRuleEntity entity);

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
