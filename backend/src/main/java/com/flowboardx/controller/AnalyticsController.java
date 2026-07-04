package com.flowboardx.controller;

import com.flowboardx.dto.AnalyticsResponse;
import com.flowboardx.dto.DashboardStatsResponse;
import com.flowboardx.service.AnalyticsService;
import com.flowboardx.web.response.ApiResponse;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/analytics")
@RequiredArgsConstructor
@Tag(name = "Analytics", description = "Dashboard summary stats and execution analytics")
public class AnalyticsController {

    private final AnalyticsService analyticsService;

    @Operation(summary = "Get dashboard summary stats")
    @GetMapping("/dashboard")
    public ResponseEntity<ApiResponse<DashboardStatsResponse>> dashboard() {
        return ResponseEntity.ok(ApiResponse.ok(analyticsService.getDashboardStats()));
    }

    @Operation(summary = "Get execution analytics (runs per day, success rate trend, etc.)")
    @GetMapping
    public ResponseEntity<ApiResponse<AnalyticsResponse>> analytics() {
        return ResponseEntity.ok(ApiResponse.ok(analyticsService.getAnalytics()));
    }
}