package com.flowboardx.dto;

import lombok.Builder;
import lombok.Getter;

import java.time.Instant;
import java.util.UUID;

@Getter
@Builder
public class QueueActivityEventResponse {
    /** ENQUEUED | DEQUEUED | NODE_STARTED | RETRY | NODE_FAILED | RUN_COMPLETED | RUN_FAILED */
    private String type;
    private Instant timestamp;
    private UUID runId;
    private String workflowName;
    private String nodeLabel;
    private Integer workerId;
    private String message;
}