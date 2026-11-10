package com.example.adplatform.admin.service;

import com.example.adplatform.admin.request.slot.DeleteSlotRequest;
import com.example.adplatform.admin.request.slot.PageSlotRequest;
import com.example.adplatform.admin.request.slot.CreateSlotRequest;
import com.example.adplatform.admin.request.slot.UpdateSlotRequest;
import com.example.adplatform.admin.request.slot.UpdateSlotStatusRequest;
import com.example.adplatform.admin.response.slot.SlotOperationResponse;
import com.example.adplatform.admin.response.slot.SlotQueryResponse;
import com.example.adplatform.admin.response.PageResponse;

public interface SlotService {

    SlotOperationResponse create(CreateSlotRequest request);

    PageResponse<SlotQueryResponse> pageQuery(PageSlotRequest request);

    SlotOperationResponse update(UpdateSlotRequest request);

    SlotOperationResponse delete(DeleteSlotRequest request);

    SlotOperationResponse updateStatus(UpdateSlotStatusRequest request);

}
