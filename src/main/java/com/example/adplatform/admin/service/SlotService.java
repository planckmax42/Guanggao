package com.example.adplatform.admin.service;

import com.example.adplatform.admin.request.CreateSlotRequest;
import com.example.adplatform.admin.request.UpdateSlotRequest;
import com.example.adplatform.admin.request.UpdateSlotStatusRequest;
import com.example.adplatform.admin.response.AvailableSlotResponse;
import com.example.adplatform.admin.response.SlotResponse;
import com.example.adplatform.common.response.PageResponse;

import java.util.List;

public interface SlotService {

    SlotResponse create(CreateSlotRequest request);

    SlotResponse update(UpdateSlotRequest request);

    SlotResponse updateStatus(UpdateSlotStatusRequest request);

    PageResponse<SlotResponse> pageQuery(long current, long size, String slotCode, Integer status);

    List<AvailableSlotResponse> listAvailable();
}
