package com.example.adplatform.admin.service;

import com.example.adplatform.admin.request.CreatePlanRequest;
import com.example.adplatform.admin.request.UpdatePlanRequest;
import com.example.adplatform.admin.response.PlanResponse;
import com.example.adplatform.common.response.PageResponse;
import com.example.adplatform.common.response.ResourceRefResponse;

public interface PlanService {

    ResourceRefResponse create(CreatePlanRequest request);

    PlanResponse update(String publicId, UpdatePlanRequest request);

    PlanResponse online(String publicId);

    PlanResponse pause(String publicId);

    PlanResponse offline(String publicId);

    PageResponse<PlanResponse> pageQuery(long current, long size, String advertiserPublicId, String status);
}
