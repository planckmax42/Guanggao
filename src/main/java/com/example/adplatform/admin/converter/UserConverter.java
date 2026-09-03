package com.example.adplatform.admin.converter;

import com.example.adplatform.admin.request.CreateUserRequest;
import com.example.adplatform.admin.entity.UserEntity;
import com.example.adplatform.admin.response.UserResponse;
import com.example.adplatform.common.enums.CommonStatus;
import com.example.adplatform.common.response.ResourceRefResponse;
import org.mapstruct.Mapper;
import org.mapstruct.Mapping;

@Mapper(componentModel = "spring", imports = CommonStatus.class)
public interface UserConverter {

    @Mapping(target = "id", ignore = true)
    @Mapping(target = "publicId", ignore = true)
    @Mapping(target = "status", expression = "java(CommonStatus.ENABLED)")
    @Mapping(target = "createdAt", ignore = true)
    @Mapping(target = "updatedAt", ignore = true)
    UserEntity toEntity(CreateUserRequest request);

    UserResponse toResponse(UserEntity entity);

    @Mapping(target = "publicId", source = "publicId")
    @Mapping(target = "bizKey", source = "name")
    ResourceRefResponse toRef(UserEntity entity);
}
