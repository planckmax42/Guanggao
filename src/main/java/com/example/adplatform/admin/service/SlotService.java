package com.example.adplatform.admin.service;

import com.example.adplatform.admin.request.CreateSlotRequest;
import com.example.adplatform.admin.request.UpdateSlotRequest;
import com.example.adplatform.admin.request.UpdateSlotStatusRequest;
import com.example.adplatform.admin.response.AvailableSlotResponse;
import com.example.adplatform.admin.response.slot.SlotQueryResponse;
import com.example.adplatform.admin.response.slot.SlotResponse;
import com.example.adplatform.admin.response.PageResponse;

import java.util.List;

public interface SlotService {

    SlotResponse create(CreateSlotRequest request);

    SlotResponse update(UpdateSlotRequest request);

    SlotResponse updateStatus(UpdateSlotStatusRequest request);

    PageResponse<SlotQueryResponse> pageQuery(long current, long size, String slotCode, Integer status);

}
