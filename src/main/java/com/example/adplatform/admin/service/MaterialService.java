package com.example.adplatform.admin.service;

import com.example.adplatform.admin.dto.AuditMaterialRequest;
import com.example.adplatform.admin.dto.CreateMaterialRequest;
import com.example.adplatform.admin.vo.MaterialVO;
import com.example.adplatform.common.response.PageResponse;
import com.example.adplatform.common.response.ResourceRefVO;

public interface MaterialService {

    ResourceRefVO create(CreateMaterialRequest request);

    MaterialVO audit(Long id, AuditMaterialRequest request);

    PageResponse<MaterialVO> pageQuery(long current, long size, Long planId, String auditStatus);
}
