package com.flowboardx.dto;

import lombok.Builder;
import lombok.Getter;

import java.time.Instant;
import java.util.UUID;

@Getter
@Builder
public class WorkerStatusResponse {
    private int workerId;
    private String status; // IDLE | ACTIVE
    private UUID currentRunId;
    private String currentWorkflowName;
    private String currentNodeLabel;
    private long completedJobs;
    private long failedJobs;
    private Instant lastHeartbeat;
    private long currentDurationMs;
}