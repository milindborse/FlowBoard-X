package com.flowboardx.controller;

import com.flowboardx.domain.entity.User;
import com.flowboardx.dto.*;
import com.flowboardx.service.ExecutionService;
import com.flowboardx.service.WorkflowService;
import com.flowboardx.web.response.ApiResponse;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.*;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.UUID;

/**
 * Execution/run endpoints. NOTE: none of the execution logic (DAG engine, retries, Redis queue,
 * WebSocket broadcasting) is touched here — this class only adds the API/ownership layer on top
 * of the existing {@link ExecutionService}, which is unchanged.
 */
@RestController
@RequestMapping("/api")
@RequiredArgsConstructor
@Tag(name = "Runs", description = "Triggering, monitoring, and controlling workflow executions")
public class RunController {

    private final ExecutionService executionService;
    private final WorkflowService workflowService;

    @Operation(summary = "Execute a workflow", description = "Triggers a new run of the workflow's latest saved version.")
    @PostMapping("/workflows/{workflowId}/execute")
    public ResponseEntity<ApiResponse<RunResponse>> execute(
            @PathVariable UUID workflowId,
            @RequestBody TriggerRunRequest request,
            @AuthenticationPrincipal User actor) {
        workflowService.getWorkflow(workflowId, actor); // ownership check; throws 403/404 as needed
        RunResponse run = executionService.triggerRun(workflowId, request, actor);
        return ResponseEntity.status(HttpStatus.CREATED).body(ApiResponse.created(run, "Run triggered"));
    }

    @Operation(summary = "Get run history for a workflow", description = "Paginated list of past runs, most recent first.")
    @GetMapping("/workflows/{workflowId}/history")
    public ResponseEntity<ApiResponse<Page<RunResponse>>> history(
            @PathVariable UUID workflowId,
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "20") int size,
            @AuthenticationPrincipal User actor) {
        workflowService.getWorkflow(workflowId, actor); // ownership check
        Page<RunResponse> runs = executionService.listRuns(workflowId, PageRequest.of(page, size));
        return ResponseEntity.ok(ApiResponse.ok(runs));
    }

    @Operation(summary = "Get a single run by ID")
    @GetMapping("/runs/{runId}")
    public ResponseEntity<ApiResponse<RunResponse>> getRun(@PathVariable UUID runId, @AuthenticationPrincipal User actor) {
        RunResponse run = executionService.getRun(runId);
        workflowService.getWorkflow(run.getWorkflowId(), actor); // ownership check
        return ResponseEntity.ok(ApiResponse.ok(run));
    }

    @Operation(summary = "Get per-node execution details for a run")
    @GetMapping("/runs/{runId}/nodes")
    public ResponseEntity<ApiResponse<List<NodeExecutionResponse>>> getNodeExecutions(
            @PathVariable UUID runId, @AuthenticationPrincipal User actor) {
        RunResponse run = executionService.getRun(runId);
        workflowService.getWorkflow(run.getWorkflowId(), actor); // ownership check
        return ResponseEntity.ok(ApiResponse.ok(executionService.getNodeExecutions(runId)));
    }

    @Operation(summary = "Cancel a running execution")
    @PostMapping("/runs/{runId}/cancel")
    public ResponseEntity<ApiResponse<Void>> cancel(@PathVariable UUID runId, @AuthenticationPrincipal User actor) {
        RunResponse run = executionService.getRun(runId);
        workflowService.getWorkflow(run.getWorkflowId(), actor); // ownership check
        executionService.cancelRun(runId);
        return ResponseEntity.status(HttpStatus.NO_CONTENT).body(ApiResponse.noContent("Run cancelled"));
    }

    @Operation(summary = "Approve a node awaiting manual approval, resuming the run")
    @PostMapping("/runs/{runId}/nodes/{nodeId}/approve")
    public ResponseEntity<ApiResponse<Void>> approve(
            @PathVariable UUID runId,
            @PathVariable String nodeId,
            @AuthenticationPrincipal User actor) {
        RunResponse run = executionService.getRun(runId);
        workflowService.getWorkflow(run.getWorkflowId(), actor); // ownership check
        executionService.approveNode(runId, nodeId, actor);
        return ResponseEntity.status(HttpStatus.NO_CONTENT).body(ApiResponse.noContent("Node approved"));
    }
}