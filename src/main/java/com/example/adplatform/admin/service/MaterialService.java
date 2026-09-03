package com.example.adplatform.admin.service;

import com.example.adplatform.admin.request.AuditMaterialRequest;
import com.example.adplatform.admin.request.CreateMaterialRequest;
import com.example.adplatform.admin.response.MaterialResponse;
import com.example.adplatform.common.response.PageResponse;
import com.example.adplatform.common.response.ResourceRefResponse;

public interface MaterialService {

    ResourceRefResponse create(CreateMaterialRequest request);

    MaterialResponse audit(String publicId, AuditMaterialRequest request);

    PageResponse<MaterialResponse> pageQuery(long current, long size, String planPublicId, String auditStatus);
}
