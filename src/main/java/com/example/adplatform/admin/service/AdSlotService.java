package com.example.adplatform.admin.service;

import com.example.adplatform.admin.dto.CreateAdSlotRequest;
import com.example.adplatform.admin.vo.AdSlotVO;
import com.example.adplatform.common.response.PageResponse;
import com.example.adplatform.common.response.ResourceRefVO;

public interface AdSlotService {

    ResourceRefVO create(CreateAdSlotRequest request);

    PageResponse<AdSlotVO> pageQuery(long current, long size, String slotCode, Integer status);
}
