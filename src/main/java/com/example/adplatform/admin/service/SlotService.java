package com.example.adplatform.admin.service;

import com.example.adplatform.admin.request.CreateSlotRequest;
import com.example.adplatform.admin.request.UpdateSlotRequest;
import com.example.adplatform.admin.request.UpdateSlotStatusRequest;
import com.example.adplatform.admin.response.slot.SlotOperationResponse;
import com.example.adplatform.admin.response.slot.SlotQueryResponse;
import com.example.adplatform.admin.response.PageResponse;

public interface SlotService {

    SlotOperationResponse create(CreateSlotRequest request);

    SlotOperationResponse update(UpdateSlotRequest request);

    SlotOperationResponse updateStatus(UpdateSlotStatusRequest request);

    PageResponse<SlotQueryResponse> pageQuery(long current, long size, String slotCode, Integer status);

}
