package com.flowboardx.controller;

import com.flowboardx.domain.entity.User;
import com.flowboardx.domain.enums.WorkflowStatus;
import com.flowboardx.dto.*;
import com.flowboardx.service.WorkflowService;
import com.flowboardx.web.response.ApiResponse;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.responses.ApiResponses;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.*;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.UUID;

@RestController
@RequestMapping("/api/workflows")
@RequiredArgsConstructor
@Tag(name = "Workflows", description = "Workflow metadata, versions, and lifecycle management")
public class WorkflowController {

    private final WorkflowService workflowService;

    @Operation(summary = "List workflows", description =
            "Paginated, filterable, sortable listing scoped to the authenticated user's own workflows.")
    @ApiResponses
    @GetMapping
    public ResponseEntity<ApiResponse<Page<WorkflowResponse>>> list(
            @Parameter(description = "Case-insensitive partial name filter") @RequestParam(required = false) String name,
            @Parameter(description = "Lifecycle status filter") @RequestParam(required = false) WorkflowStatus status,
            @Parameter(description = "Exact current version-number filter") @RequestParam(required = false) Integer version,
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "20") int size,
            @Parameter(description = "Sort field: updatedAt, createdAt, name") @RequestParam(defaultValue = "updatedAt") String sortBy,
            @RequestParam(defaultValue = "desc") String direction,
            @AuthenticationPrincipal User actor) {
        Sort sort = Sort.by(Sort.Direction.fromString(direction), sortBy);
        Page<WorkflowResponse> result = workflowService.listWorkflows(
                name, status, version, PageRequest.of(page, size, sort), actor);
        return ResponseEntity.ok(ApiResponse.ok(result));
    }

    @Operation(summary = "List reusable workflow templates")
    @GetMapping("/templates")
    public ResponseEntity<ApiResponse<List<WorkflowResponse>>> templates() {
        return ResponseEntity.ok(ApiResponse.ok(workflowService.getTemplates()));
    }

    @Operation(summary = "Get a single workflow by ID")
    @GetMapping("/{id}")
    public ResponseEntity<ApiResponse<WorkflowResponse>> get(@PathVariable UUID id, @AuthenticationPrincipal User actor) {
        return ResponseEntity.ok(ApiResponse.ok(workflowService.getWorkflow(id, actor)));
    }

    @Operation(summary = "Create workflow metadata",
            description = "Step 1 of the create flow: saves name/description/category only. " +
                    "The builder canvas should be opened by the client only after this returns 201.")
    @PostMapping
    public ResponseEntity<ApiResponse<WorkflowResponse>> create(
            @Valid @RequestBody CreateWorkflowRequest request,
            @AuthenticationPrincipal User actor) {
        WorkflowResponse created = workflowService.createWorkflow(request, actor);
        return ResponseEntity.status(HttpStatus.CREATED)
                .body(ApiResponse.created(created, "Workflow created successfully"));
    }

    @Operation(summary = "Update workflow metadata (name remains editable after creation)")
    @PutMapping("/{id}")
    public ResponseEntity<ApiResponse<WorkflowResponse>> update(
            @PathVariable UUID id,
            @Valid @RequestBody WorkflowRequest request,
            @AuthenticationPrincipal User actor) {
        return ResponseEntity.ok(ApiResponse.ok(workflowService.updateWorkflow(id, request, actor), "Workflow updated"));
    }

    @Operation(summary = "Publish a workflow (marks lifecycle status as PUBLISHED)")
    @PatchMapping("/{id}/publish")
    public ResponseEntity<ApiResponse<WorkflowResponse>> publish(
            @PathVariable UUID id, @AuthenticationPrincipal User actor) {
        return ResponseEntity.ok(ApiResponse.ok(workflowService.publishWorkflow(id, actor), "Workflow published"));
    }

    @Operation(summary = "Soft-delete a workflow")
    @DeleteMapping("/{id}")
    public ResponseEntity<ApiResponse<Void>> delete(@PathVariable UUID id, @AuthenticationPrincipal User actor) {
        workflowService.deleteWorkflow(id, actor);
        return ResponseEntity.status(HttpStatus.NO_CONTENT).body(ApiResponse.noContent("Workflow deleted"));
    }

    // ── Versions ───────────────────────────────────────────────────────────────

    @Operation(summary = "Save a new workflow version (draft or published)")
    @PostMapping("/{id}/versions")
    public ResponseEntity<ApiResponse<WorkflowVersionResponse>> saveVersion(
            @PathVariable UUID id,
            @Valid @RequestBody SaveVersionRequest request,
            @AuthenticationPrincipal User actor) {
        WorkflowVersionResponse saved = workflowService.saveVersion(id, request, actor);
        return ResponseEntity.status(HttpStatus.CREATED).body(ApiResponse.created(saved, "Version saved"));
    }

    @Operation(summary = "Get the latest saved version of a workflow")
    @GetMapping("/{id}/versions/latest")
    public ResponseEntity<ApiResponse<WorkflowVersionResponse>> latestVersion(
            @PathVariable UUID id, @AuthenticationPrincipal User actor) {
        return ResponseEntity.ok(ApiResponse.ok(workflowService.getVersion(id, null, actor)));
    }

    @Operation(summary = "Get a specific workflow version by number (i.e. version history entry)")
    @GetMapping("/{id}/versions/{versionNumber}")
    public ResponseEntity<ApiResponse<WorkflowVersionResponse>> getVersion(
            @PathVariable UUID id, @PathVariable Integer versionNumber, @AuthenticationPrincipal User actor) {
        return ResponseEntity.ok(ApiResponse.ok(workflowService.getVersion(id, versionNumber, actor)));
    }
}