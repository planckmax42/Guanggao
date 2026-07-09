package com.example.adplatform.admin.service;

import com.example.adplatform.admin.dto.AuditCreativeRequest;
import com.example.adplatform.admin.dto.CreateCreativeRequest;
import com.example.adplatform.admin.vo.CreativeVO;
import com.example.adplatform.common.response.PageResponse;
import com.example.adplatform.common.response.ResourceRefVO;

public interface CreativeService {

    ResourceRefVO create(CreateCreativeRequest request);

    CreativeVO audit(Long id, AuditCreativeRequest request);

    PageResponse<CreativeVO> pageQuery(long current, long size, Long campaignId, String auditStatus);
}
