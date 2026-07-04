package com.flowboardx.dto;

import lombok.Builder;
import lombok.Getter;

import java.util.List;

@Getter @Builder
public class DashboardStatsResponse {
    private long totalWorkflows;
    private long totalRuns;
    private long successfulRuns;
    private long failedRuns;
    private long runningRuns;
    private double successRate;
    private Long avgDurationMs;
    private List<RunResponse> recentRuns;
}
