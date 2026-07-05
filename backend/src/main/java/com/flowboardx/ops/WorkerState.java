package com.flowboardx.ops;

import lombok.Getter;
import lombok.Setter;

import java.time.Instant;
import java.util.UUID;

/** Mutable, in-memory live state for one polling worker thread. Not a JPA entity. */
@Getter
@Setter
public class WorkerState {

    public enum Status { IDLE, ACTIVE }

    private final int workerId;
    private volatile Status status = Status.IDLE;
    private volatile UUID currentRunId;
    private volatile String currentWorkflowName;
    private volatile String currentNodeLabel;
    private volatile long completedJobs = 0;
    private volatile long failedJobs = 0;
    private volatile Instant lastHeartbeat = Instant.now();
    private volatile Instant currentJobStartedAt;

    public WorkerState(int workerId) {
        this.workerId = workerId;
    }

    public synchronized void markActive(UUID runId, String workflowName) {
        this.status = Status.ACTIVE;
        this.currentRunId = runId;
        this.currentWorkflowName = workflowName;
        this.currentNodeLabel = null;
        this.currentJobStartedAt = Instant.now();
        this.lastHeartbeat = Instant.now();
    }

    public synchronized void markIdle(boolean succeeded) {
        if (this.status == Status.ACTIVE) {
            if (succeeded) completedJobs++; else failedJobs++;
        }
        this.status = Status.IDLE;
        this.currentRunId = null;
        this.currentWorkflowName = null;
        this.currentNodeLabel = null;
        this.currentJobStartedAt = null;
        this.lastHeartbeat = Instant.now();
    }

    public synchronized void updateCurrentNode(String nodeLabel) {
        this.currentNodeLabel = nodeLabel;
        this.lastHeartbeat = Instant.now();
    }

    public long currentDurationMs() {
        return currentJobStartedAt == null ? 0 : Instant.now().toEpochMilli() - currentJobStartedAt.toEpochMilli();
    }
}