package com.example.adplatform.admin.service;

import com.example.adplatform.admin.request.plan.DeletePlanRequest;
import com.example.adplatform.admin.request.plan.PagePlanRequest;
import com.example.adplatform.admin.request.plan.UpdatePlanStatusRequest;
import com.example.adplatform.admin.request.plan.CreatePlanRequest;
import com.example.adplatform.admin.request.plan.UpdatePlanRequest;
import com.example.adplatform.admin.response.plan.PlanOperationResponse;
import com.example.adplatform.admin.response.plan.PlanResponse;
import com.example.adplatform.admin.response.PageResponse;

public interface PlanService {

    PlanOperationResponse create(CreatePlanRequest request);

    PageResponse<PlanResponse> pageQuery(PagePlanRequest request);

    PlanOperationResponse update(UpdatePlanRequest request);

    PlanOperationResponse delete(DeletePlanRequest request);

    PlanResponse online(UpdatePlanStatusRequest request);

    PlanResponse pause(UpdatePlanStatusRequest request);

    PlanResponse offline(UpdatePlanStatusRequest request);
}
