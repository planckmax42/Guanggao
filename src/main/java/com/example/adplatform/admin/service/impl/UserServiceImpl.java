package com.example.adplatform.admin.service.impl;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.example.adplatform.admin.converter.UserConverter;
import com.example.adplatform.admin.request.CreateUserRequest;
import com.example.adplatform.admin.entity.UserEntity;
import com.example.adplatform.admin.mapper.UserMapper;
import com.example.adplatform.admin.service.UserService;
import com.example.adplatform.admin.response.UserResponse;
import com.example.adplatform.common.exception.BusinessException;
import com.example.adplatform.common.exception.ErrorCode;
import com.example.adplatform.common.response.PageResponse;
import com.example.adplatform.common.response.ResourceRefResponse;
import lombok.RequiredArgsConstructor;
import org.springframework.dao.DuplicateKeyException;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;

import java.util.List;

@RequiredArgsConstructor
@Service
public class UserServiceImpl implements UserService {

    private final UserMapper userMapper;
    private final UserConverter userConverter;

    @Override
    public ResourceRefResponse create(CreateUserRequest request) {
        UserEntity entity = userConverter.toEntity(request);
        try {
            userMapper.insert(entity);
        } catch (DuplicateKeyException ex) {
            throw new BusinessException(ErrorCode.DUPLICATE_RESOURCE, "广告主名称已存在");
        }
        return userConverter.toRef(entity);
    }

    @Override
    public PageResponse<UserResponse> pageQuery(long current, long size, String name, Integer status) {
        Page<UserEntity> page = new Page<>(current, size);
        LambdaQueryWrapper<UserEntity> query = new LambdaQueryWrapper<UserEntity>()
                .like(StringUtils.hasText(name), UserEntity::getName, name)
                .eq(status != null, UserEntity::getStatus, status)
                .orderByDesc(UserEntity::getId);
        Page<UserEntity> result = userMapper.selectPage(page, query);
        List<UserResponse> records = result.getRecords().stream().map(userConverter::toResponse).toList();
        return PageResponse.of(result, records);
    }
}
