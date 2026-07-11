package com.example.adplatform.admin.converter;

import com.example.adplatform.admin.dto.CreateUserRequest;
import com.example.adplatform.admin.entity.UserEntity;
import com.example.adplatform.admin.vo.UserVO;
import com.example.adplatform.common.enums.CommonStatus;
import com.example.adplatform.common.response.ResourceRefVO;
import org.mapstruct.Mapper;
import org.mapstruct.Mapping;

@Mapper(componentModel = "spring", imports = CommonStatus.class)
public interface UserConverter {

    @Mapping(target = "id", ignore = true)
    @Mapping(target = "status", expression = "java(CommonStatus.ENABLED)")
    @Mapping(target = "createdAt", ignore = true)
    @Mapping(target = "updatedAt", ignore = true)
    UserEntity toEntity(CreateUserRequest request);

    UserVO toVO(UserEntity entity);

    @Mapping(target = "bizKey", source = "name")
    ResourceRefVO toRef(UserEntity entity);
}
