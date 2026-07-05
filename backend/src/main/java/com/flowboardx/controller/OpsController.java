package com.flowboardx.controller;

import com.flowboardx.dto.QueueActivityEventResponse;
import com.flowboardx.dto.QueueMetricsResponse;
import com.flowboardx.ops.QueueMetricsService;
import com.flowboardx.web.response.ApiResponse;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.List;

/**
 * Queue Operations Dashboard endpoints. This is an ops/admin-style view - it shows queue and
 * worker state across ALL users' runs system-wide, not scoped to the requesting user, since
 * the whole point is visibility into the shared distributed execution layer.
 */
@RestController
@RequestMapping("/api/ops")
@RequiredArgsConstructor
@Tag(name = "Ops", description = "Queue Operations Dashboard: live queue/worker metrics and activity feed")
public class OpsController {

    private final QueueMetricsService metricsService;

    @Operation(summary = "Get a full snapshot of queue/worker metrics and trend series")
    @GetMapping("/queue-metrics")
    public ResponseEntity<ApiResponse<QueueMetricsResponse>> queueMetrics() {
        return ResponseEntity.ok(ApiResponse.ok(metricsService.snapshot()));
    }

    @Operation(summary = "Get the most recent queue activity events (also streamed live over WebSocket)")
    @GetMapping("/activity-feed")
    public ResponseEntity<ApiResponse<List<QueueActivityEventResponse>>> activityFeed(
            @RequestParam(defaultValue = "50") int limit) {
        return ResponseEntity.ok(ApiResponse.ok(metricsService.recentActivity(limit)));
    }

    @Operation(summary = "Reset accumulated counters/trends/activity feed (does not affect the live queue or in-flight runs)")
    @PostMapping("/reset-metrics")
    public ResponseEntity<ApiResponse<Void>> resetMetrics() {
        metricsService.reset();
        return ResponseEntity.ok(ApiResponse.noContent("Metrics reset"));
    }
}