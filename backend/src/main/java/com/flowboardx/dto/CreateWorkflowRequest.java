package com.flowboardx.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import lombok.Getter;
import lombok.Setter;

/**
 * Payload for the "Create Workflow" modal. Only metadata — no nodes/edges yet.
 * The builder canvas opens only after this succeeds.
 */
@Getter
@Setter
@Schema(description = "Metadata required to create a new workflow, before the builder opens")
public class CreateWorkflowRequest {

    @NotBlank(message = "Workflow name is required")
    @Size(min = 1, max = 150, message = "Workflow name must be between 1 and 150 characters")
    @Schema(example = "Customer Onboarding Pipeline")
    private String name;

    @Size(max = 1000, message = "Description must not exceed 1000 characters")
    @Schema(example = "Runs KYC checks and provisions the new customer account")
    private String description;

    @Size(max = 100, message = "Category must not exceed 100 characters")
    @Schema(example = "Finance")
    private String category;
}