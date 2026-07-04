package com.flowboardx.dto;

import jakarta.validation.constraints.NotBlank;
import lombok.Getter;
import lombok.Setter;

@Getter @Setter
public class EdgeDto {
    @NotBlank
    private String sourceClientNodeId;
    @NotBlank
    private String targetClientNodeId;
    private String branchLabel;
}
