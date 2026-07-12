package com.example.adplatform.admin.service;

import com.example.adplatform.admin.dto.CreateSlotRequest;
import com.example.adplatform.admin.dto.UpdateSlotRequest;
import com.example.adplatform.admin.vo.SlotVO;
import com.example.adplatform.common.response.PageResponse;
import com.example.adplatform.common.response.ResourceRefVO;

public interface SlotService {

    ResourceRefVO create(CreateSlotRequest request);

    SlotVO update(Long id, UpdateSlotRequest request);

    PageResponse<SlotVO> pageQuery(long current, long size, String slotCode, Integer status);
}
