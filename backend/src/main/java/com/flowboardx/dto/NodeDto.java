package com.flowboardx.dto;

import com.flowboardx.domain.enums.NodeType;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import lombok.Getter;
import lombok.Setter;

@Getter @Setter
public class NodeDto {
    @NotBlank
    private String clientNodeId;
    @NotBlank
    private String label;
    @NotNull
    private NodeType type;
    private double positionX;
    private double positionY;
    private String configJson;
    private Integer retryMaxAttempts;
    private Long retryBaseBackoffMs;
}
