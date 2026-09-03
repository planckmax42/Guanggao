package com.example.adplatform.admin.controller;

import com.example.adplatform.admin.request.CreateUserRequest;
import com.example.adplatform.admin.service.UserService;
import com.example.adplatform.admin.response.UserResponse;
import com.example.adplatform.common.response.PageResponse;
import com.example.adplatform.common.response.ResourceRefResponse;
import com.example.adplatform.common.response.Result;
import jakarta.validation.Valid;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import lombok.RequiredArgsConstructor;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@Validated
@RequiredArgsConstructor
@RestController
@RequestMapping("/api/platform/advertisers")
public class UserController {

    private final UserService userService;

    /**
     * 创建广告主账号，作为广告计划的归属主体。
     */
    @PostMapping
    public Result<ResourceRefResponse> create(@Valid @RequestBody CreateUserRequest request) {
        return Result.success(userService.create(request));
    }

    /**
     * 分页查询广告主，支持按名称关键字和启用状态筛选。
     */
    @GetMapping("/page")
    public Result<PageResponse<UserResponse>> page(
            @RequestParam(defaultValue = "1") @Min(1) long current,
            @RequestParam(defaultValue = "10") @Min(1) @Max(100) long size,
            @RequestParam(required = false) String name,
            @RequestParam(required = false) Integer status) {
        return Result.success(userService.pageQuery(current, size, name, status));
    }
}
