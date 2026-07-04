package com.flowboardx.dto;

import com.flowboardx.domain.enums.WorkflowStatus;
import lombok.Builder;
import lombok.Getter;

import java.time.Instant;
import java.util.UUID;

@Getter
@Builder
public class WorkflowResponse {
    private UUID id;
    private String name;
    private String description;
    private String category;
    private WorkflowStatus status;
    private UUID ownerId;
    private boolean active;
    private boolean isTemplate;
    private Integer currentVersionNumber;
    private String cronExpression;
    private Instant createdAt;
    private Instant updatedAt;
}