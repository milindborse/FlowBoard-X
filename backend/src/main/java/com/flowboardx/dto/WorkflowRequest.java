package com.flowboardx.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import lombok.Getter;
import lombok.Setter;

/**
 * Payload for updating workflow metadata (PUT /api/workflows/{id}).
 * Name remains editable after creation, per spec.
 */
@Getter
@Setter
@Schema(description = "Payload for updating an existing workflow's metadata")
public class WorkflowRequest {

    @NotBlank(message = "Workflow name is required")
    @Size(min = 1, max = 150, message = "Workflow name must be between 1 and 150 characters")
    private String name;

    @Size(max = 1000, message = "Description must not exceed 1000 characters")
    private String description;

    @Size(max = 100, message = "Category must not exceed 100 characters")
    private String category;

    private String cronExpression;

    private boolean isTemplate;
}