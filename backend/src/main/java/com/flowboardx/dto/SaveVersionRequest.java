package com.flowboardx.dto;

import jakarta.validation.Valid;
import lombok.Getter;
import lombok.Setter;

import java.util.List;

@Getter @Setter
public class SaveVersionRequest {
    @Valid
    private List<NodeDto> nodes;
    @Valid
    private List<EdgeDto> edges;
    private String changeSummary;
    private boolean publish;
}
