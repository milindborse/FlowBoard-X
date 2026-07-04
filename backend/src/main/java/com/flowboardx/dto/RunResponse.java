package com.flowboardx.dto;

import com.flowboardx.domain.enums.RunStatus;
import com.flowboardx.domain.enums.TriggerType;
import lombok.Builder;
import lombok.Getter;

import java.time.Instant;
import java.util.UUID;

@Getter @Builder
public class RunResponse {
    private UUID id;
    private UUID workflowId;
    private String workflowName;
    private Integer versionNumber;
    private RunStatus status;
    private TriggerType triggerType;
    private UUID replayedFromRunId;
    private String errorMessage;
    private Instant startedAt;
    private Instant finishedAt;
    private Long durationMs;
    private Instant createdAt;
}
