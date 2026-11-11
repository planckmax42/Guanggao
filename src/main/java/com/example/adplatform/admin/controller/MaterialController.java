package com.example.adplatform.admin.controller;

import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.DeleteMapping;
import com.example.adplatform.admin.request.material.UpdateMaterialRequest;
import com.example.adplatform.admin.request.material.DeleteMaterialRequest;
import com.example.adplatform.admin.request.material.CreateMaterialRequest;
import com.example.adplatform.admin.request.material.PageMaterialRequest;
import com.example.adplatform.admin.response.material.MaterialOperationResponse;
import com.example.adplatform.admin.service.MaterialService;
import com.example.adplatform.admin.response.material.MaterialQueryResponse;
import com.example.adplatform.admin.response.PageResponse;
import com.example.adplatform.common.response.Result;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@Validated
@RequiredArgsConstructor
@RestController
@RequestMapping("/api/advertiser/materials")
public class MaterialController {

    private final MaterialService materialService;

    /**
     * 为已有广告主创建广告素材，初始审核状态为待审核。
     */
    @PostMapping("/create")
    public Result<MaterialOperationResponse> create(@Valid @RequestBody CreateMaterialRequest request) {
        return Result.success(materialService.create(request));
    }

    /**
     * 分页查询广告素材。
     */
    @PostMapping("/page")
    public Result<PageResponse<MaterialQueryResponse>> page(@Valid @RequestBody PageMaterialRequest request) {
        return Result.success(materialService.pageQuery(request));
    }

    @PutMapping("/update")
    public Result<MaterialOperationResponse> update(@Valid @RequestBody UpdateMaterialRequest request) {
        return Result.success(materialService.update(request));
    }

    @DeleteMapping("/delete")
    public Result<MaterialOperationResponse> delete(@Valid @RequestBody DeleteMaterialRequest request) {
        return Result.success(materialService.delete(request));
    }
}
