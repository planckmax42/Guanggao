package com.example.adplatform.admin.controller;

import org.springframework.web.bind.annotation.DeleteMapping;
import com.example.adplatform.admin.request.plan.DeletePlanRequest;
import com.example.adplatform.admin.request.plan.CreatePlanRequest;
import com.example.adplatform.admin.request.plan.PagePlanRequest;
import com.example.adplatform.admin.request.plan.UpdatePlanRequest;
import com.example.adplatform.admin.request.plan.UpdatePlanStatusRequest;
import com.example.adplatform.admin.response.plan.PlanOperationResponse;
import com.example.adplatform.admin.service.PlanService;
import com.example.adplatform.admin.response.plan.PlanResponse;
import com.example.adplatform.admin.response.PageResponse;
import com.example.adplatform.common.response.Result;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@Validated
@RequiredArgsConstructor
@RestController
@RequestMapping("/api/advertiser/plans")
public class PlanController {

    private final PlanService planService;

    @PostMapping("/create")
    public Result<PlanOperationResponse> create(@Valid @RequestBody CreatePlanRequest request) {
        return Result.success(planService.create(request));
    }

    @PostMapping("/page")
    public Result<PageResponse<PlanResponse>> page(@Valid @RequestBody PagePlanRequest request) {
        return Result.success(planService.pageQuery(request));
    }

    @PutMapping("/update")
    public Result<PlanOperationResponse> update(@Valid @RequestBody UpdatePlanRequest request) {
        return Result.success(planService.update(request));
    }

    @DeleteMapping("/delete")
    public Result<PlanOperationResponse> delete(@Valid @RequestBody DeletePlanRequest request) {
        return Result.success(planService.delete(request));
    }









    @PutMapping("/online")
    public Result<PlanResponse> online(@Valid @RequestBody UpdatePlanStatusRequest request) {
        return Result.success(planService.online(request));
    }

    @PutMapping("/pause")
    public Result<PlanResponse> pause(@Valid @RequestBody UpdatePlanStatusRequest request) {
        return Result.success(planService.pause(request));
    }

    @PutMapping("/offline")
    public Result<PlanResponse> offline(@Valid @RequestBody UpdatePlanStatusRequest request) {
        return Result.success(planService.offline(request));
    }
}
