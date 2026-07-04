package com.flowboardx.websocket;

import com.flowboardx.domain.enums.NodeExecutionStatus;
import lombok.Builder;
import lombok.Getter;

import java.time.Instant;
import java.util.Map;
import java.util.UUID;

/**
 * Single event broadcast over WebSocket to the live execution viewer.
 * The UI subscribes to /topic/runs/{runId} and uses these events to
 * animate node state transitions in real-time on the React Flow canvas.
 */
@Getter
@Builder
public class ExecutionEvent {

    public enum Type {
        NODE_STARTED,
        NODE_SUCCEEDED,
        NODE_FAILED,
        NODE_RETRYING,
        NODE_SKIPPED,
        NODE_AWAITING_APPROVAL,
        RUN_COMPLETED,
        RUN_FAILED,
        LOG_LINE
    }

    private Type type;
    private UUID runId;
    private String nodeId;
    private String nodeLabel;
    private NodeExecutionStatus nodeStatus;
    private Integer attemptNumber;
    private Long durationMs;
    private Long nextRetryDelayMs;
    private String message;
    private String errorMessage;
    private Map<String, Object> output;
    private Instant timestamp;
}
