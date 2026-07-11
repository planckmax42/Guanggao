package com.example.adplatform.admin.service;

import com.example.adplatform.admin.dto.CreateUserRequest;
import com.example.adplatform.admin.vo.UserVO;
import com.example.adplatform.common.response.PageResponse;
import com.example.adplatform.common.response.ResourceRefVO;

public interface UserService {

    ResourceRefVO create(CreateUserRequest request);

    PageResponse<UserVO> pageQuery(long current, long size, String name, Integer status);
}
