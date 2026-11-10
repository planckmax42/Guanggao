package com.example.adplatform.admin.service;

import com.example.adplatform.admin.request.plan.PagePlanRequest;
import com.example.adplatform.admin.request.plan.UpdatePlanStatusRequest;
import com.example.adplatform.admin.request.plan.CreatePlanRequest;
import com.example.adplatform.admin.request.plan.UpdatePlanRequest;
import com.example.adplatform.admin.response.plan.PlanResponse;
import com.example.adplatform.admin.response.PageResponse;
import com.example.adplatform.common.response.ResourceRefResponse;

public interface PlanService {

    ResourceRefResponse create(CreatePlanRequest request);

    PlanResponse update(UpdatePlanRequest request);

    PlanResponse online(UpdatePlanStatusRequest request);

    PlanResponse pause(UpdatePlanStatusRequest request);

    PlanResponse offline(UpdatePlanStatusRequest request);

    PageResponse<PlanResponse> pageQuery(PagePlanRequest request);
}
