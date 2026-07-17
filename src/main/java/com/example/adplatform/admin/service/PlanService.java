package com.example.adplatform.admin.service;

import com.example.adplatform.admin.request.CreatePlanRequest;
import com.example.adplatform.admin.request.UpdatePlanRequest;
import com.example.adplatform.admin.response.PlanResponse;
import com.example.adplatform.common.response.PageResponse;
import com.example.adplatform.common.response.ResourceRefResponse;

public interface PlanService {

    ResourceRefResponse create(CreatePlanRequest request);

    PlanResponse update(Long id, UpdatePlanRequest request);

    PlanResponse online(Long id);

    PlanResponse pause(Long id);

    PlanResponse offline(Long id);

    PageResponse<PlanResponse> pageQuery(long current, long size, Long userId, String status);
}
