package com.example.adplatform.admin.service;

import com.example.adplatform.admin.request.CreateUserRequest;
import com.example.adplatform.admin.response.UserResponse;
import com.example.adplatform.common.response.PageResponse;
import com.example.adplatform.common.response.ResourceRefResponse;

public interface UserService {

    ResourceRefResponse create(CreateUserRequest request);

    PageResponse<UserResponse> pageQuery(long current, long size, String name, Integer status);
}
