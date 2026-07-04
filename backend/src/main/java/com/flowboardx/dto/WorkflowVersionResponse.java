package com.flowboardx.dto;

import lombok.Builder;
import lombok.Getter;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

@Getter @Builder
public class WorkflowVersionResponse {
    private UUID id;
    private Integer versionNumber;
    private boolean published;
    private String changeSummary;
    private List<NodeDto> nodes;
    private List<EdgeDto> edges;
    private Instant createdAt;
}
