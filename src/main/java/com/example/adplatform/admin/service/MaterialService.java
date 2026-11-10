package com.example.adplatform.admin.service;

import com.example.adplatform.admin.request.material.AuditMaterialRequest;
import com.example.adplatform.admin.request.material.CreateMaterialRequest;
import com.example.adplatform.admin.response.material.MaterialOperationResponse;
import com.example.adplatform.admin.response.material.MaterialQueryResponse;
import com.example.adplatform.admin.response.PageResponse;

public interface MaterialService {

    MaterialOperationResponse create(CreateMaterialRequest request);

    PageResponse<MaterialQueryResponse> pageQuery(long current, long size, String planPublicId, String auditStatus);
}
