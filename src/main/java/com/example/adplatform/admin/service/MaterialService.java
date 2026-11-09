package com.example.adplatform.admin.service;

import com.example.adplatform.admin.request.material.AuditMaterialRequest;
import com.example.adplatform.admin.request.material.CreateMaterialRequest;
import com.example.adplatform.admin.response.material.MaterialResponse;
import com.example.adplatform.admin.response.PageResponse;
import com.example.adplatform.common.response.ResourceRefResponse;

public interface MaterialService {

    ResourceRefResponse create(CreateMaterialRequest request);

    MaterialResponse audit(String publicId, AuditMaterialRequest request);

    PageResponse<MaterialResponse> pageQuery(long current, long size, String planPublicId, String auditStatus);
}
