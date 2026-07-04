package com.flowboardx.dto;

import com.flowboardx.domain.enums.NodeExecutionStatus;
import lombok.Builder;
import lombok.Getter;

import java.time.Instant;
import java.util.UUID;

@Getter @Builder
public class NodeExecutionResponse {
    private UUID id;
    private String clientNodeId;
    private String nodeLabel;
    private NodeExecutionStatus status;
    private int attemptNumber;
    private String logOutput;
    private String errorMessage;
    private Instant startedAt;
    private Instant finishedAt;
    private Long durationMs;
}
