package com.example.adplatform.admin.service;

import com.example.adplatform.admin.request.CreateSlotRequest;
import com.example.adplatform.admin.request.UpdateSlotRequest;
import com.example.adplatform.admin.response.AvailableSlotResponse;
import com.example.adplatform.admin.response.SlotResponse;
import com.example.adplatform.common.response.PageResponse;
import com.example.adplatform.common.response.ResourceRefResponse;

import java.util.List;

public interface SlotService {

    ResourceRefResponse create(CreateSlotRequest request);

    SlotResponse update(String publicId, UpdateSlotRequest request);

    PageResponse<SlotResponse> pageQuery(long current, long size, String slotCode, Integer status);

    List<AvailableSlotResponse> listAvailable();
}
