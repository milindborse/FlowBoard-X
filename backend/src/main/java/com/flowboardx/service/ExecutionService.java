package com.flowboardx.service;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.flowboardx.domain.entity.*;
import com.flowboardx.domain.enums.*;
import com.flowboardx.dto.*;
import com.flowboardx.engine.*;
import com.flowboardx.engine.executor.NodeExecutorRegistry;
import com.flowboardx.ops.QueueMetricsService;
import com.flowboardx.queue.*;
import com.flowboardx.repository.*;
import com.flowboardx.websocket.*;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.support.TransactionSynchronization;
import org.springframework.transaction.support.TransactionSynchronizationManager;

import java.time.Instant;
import java.util.*;
import java.util.stream.Collectors;

@Slf4j
@Service
@RequiredArgsConstructor
public class ExecutionService {

    private final WorkflowRepository workflowRepo;
    private final WorkflowVersionRepository versionRepo;
    private final WorkflowNodeRepository nodeRepo;
    private final WorkflowEdgeRepository edgeRepo;
    private final WorkflowRunRepository runRepo;
    private final NodeExecutionRepository nodeExecRepo;
    private final AuditLogRepository auditRepo;
    private final ExecutionQueueProducer queueProducer;
    private final DagEngine dagEngine;
    private final NodeExecutorRegistry executorRegistry;
    private final WebSocketBroadcaster broadcaster;
    private final QueueMetricsService metricsService;

    private final ObjectMapper objectMapper = new ObjectMapper();

    @Value("${flowboardx.execution.worker-pool-size:4}")
    private int workerPoolSize;
    @Value("${flowboardx.execution.max-retry-attempts:5}")
    private int maxRetryAttempts;
    @Value("${flowboardx.execution.base-backoff-ms:1000}")
    private long baseBackoffMs;

    // ── Trigger ────────────────────────────────────────────────────────────────

    @Transactional
    public RunResponse triggerRun(UUID workflowId, TriggerRunRequest request, User actor) {
        Workflow workflow = workflowRepo.findById(workflowId)
                .orElseThrow(() -> new RuntimeException("Workflow not found"));

        WorkflowVersion version = versionRepo.findTopByWorkflow_IdOrderByVersionNumberDesc(workflowId)
                .orElseThrow(() -> new RuntimeException("No saved version found. Save the workflow first."));

        WorkflowRun run = WorkflowRun.builder()
                .workflow(workflow)
                .workflowVersion(version)
                .status(RunStatus.QUEUED)
                .triggerType(request.getTriggerType())
                .replayedFromRunId(request.getReplayFromRunId())
                .inputPayloadJson(toJson(request.getInputPayload()))
                .build();
        run = runRepo.save(run);

        audit(actor, workflow, run.getId(), AuditAction.RUN_STARTED, "Run queued via " + request.getTriggerType());

        Set<String> alreadyCompleted = Collections.emptySet();
        List<String> startNodes;

        if (request.getReplayFromNodeId() != null) {
            alreadyCompleted = computeSucceededNodesUpTo(request.getReplayFromRunId(), request.getReplayFromNodeId());
            startNodes = List.of(request.getReplayFromNodeId());
            audit(actor, workflow, run.getId(), AuditAction.RUN_REPLAYED,
                    "Replaying from node " + request.getReplayFromNodeId());
        } else {
            List<WorkflowNode> rootNodes = computeRootNodes(version.getId());
            startNodes = rootNodes.stream().map(WorkflowNode::getClientNodeId).collect(Collectors.toList());
        }

        ExecutionMessage message = ExecutionMessage.builder()
                .workflowRunId(run.getId())
                .startNodeIds(startNodes)
                .alreadyCompletedNodeIds(alreadyCompleted)
                .workflowName(workflow.getName())
                .build();

        // CRITICAL: this method is @Transactional - the WorkflowRun row above isn't
        // actually committed to Postgres until this whole method returns. If we push
        // to Redis right now, the worker thread (a totally separate connection, with
        // no idea about our in-flight transaction) can pop the message and query for
        // a run that, from its point of view, doesn't exist yet -> NoSuchElementException.
        // Deferring the enqueue to AFTER_COMMIT closes that race entirely.
        enqueueAfterCommit(message);

        return toRunResponse(run);
    }

    /** Pushes to the Redis queue only once the current transaction has actually committed. */
    private void enqueueAfterCommit(ExecutionMessage message) {
        if (TransactionSynchronizationManager.isSynchronizationActive()) {
            TransactionSynchronizationManager.registerSynchronization(new TransactionSynchronization() {
                @Override
                public void afterCommit() {
                    queueProducer.enqueue(message);
                }
            });
        } else {
            queueProducer.enqueue(message); // no active transaction (e.g. called from a test) - just send it
        }
    }

    // ── Worker entry point ─────────────────────────────────────────────────────

    public void processQueuedRun(ExecutionMessage message, int workerId) {
        UUID runId = message.getWorkflowRunId();
        WorkflowRun run = runRepo.findById(runId)
                .orElseThrow(() -> new IllegalStateException(
                        "Worker picked up a queue message for run " + runId + " but no such run exists in the database. " +
                        "This should no longer happen now that enqueue is deferred to after-commit - if you see this, " +
                        "check whether something deleted the run row mid-flight."));
        log.info("Processing run {}", runId);

        run.setStatus(RunStatus.RUNNING);
        run.setStartedAt(Instant.now());
        runRepo.save(run);

        List<WorkflowNode> nodes = nodeRepo.findByWorkflowVersion_Id(run.getWorkflowVersion().getId());
        List<WorkflowEdge> edges = edgeRepo.findByWorkflowVersion_Id(run.getWorkflowVersion().getId());

        ExecutionGraph graph;
        try {
            graph = new ExecutionGraph(nodes, edges);
        } catch (CycleDetectedException e) {
            failRun(run, "Cycle detected in workflow graph: " + e.getMessage());
            return;
        }

        Map<String, Object> triggerPayload = fromJson(run.getInputPayloadJson());
        ExecutionContext context = new ExecutionContext(runId, triggerPayload);

        // Pre-populate output cache for already-completed nodes (replay case)
        if (message.getAlreadyCompletedNodeIds() != null) {
            for (String completedId : message.getAlreadyCompletedNodeIds()) {
                nodeExecRepo.findByWorkflowRun_IdAndClientNodeId(runId, completedId)
                        .stream().max(Comparator.comparingInt(NodeExecution::getAttemptNumber))
                        .ifPresent(ne -> context.recordOutput(completedId, fromJson(ne.getOutputJson())));
            }
        }

        ExecutionListener listener = buildListener(run, workerId, message.getWorkflowName());

        RunStatus finalStatus = dagEngine.execute(
                graph, context, executorRegistry, listener,
                message.getAlreadyCompletedNodeIds() == null ? Set.of() : message.getAlreadyCompletedNodeIds(),
                message.getStartNodeIds(),
                workerPoolSize, maxRetryAttempts, baseBackoffMs);

        run.setStatus(finalStatus);
        run.setFinishedAt(Instant.now());
        run.setDurationMs(run.getFinishedAt().toEpochMilli() - run.getStartedAt().toEpochMilli());
        if (finalStatus == RunStatus.FAILED) {
            run.setErrorMessage("One or more nodes failed after exhausting retry attempts");
        }
        runRepo.save(run);

        ExecutionEvent.Type endType = finalStatus == RunStatus.SUCCEEDED
                ? ExecutionEvent.Type.RUN_COMPLETED : ExecutionEvent.Type.RUN_FAILED;
        broadcaster.broadcast(runId, ExecutionEvent.builder()
                .type(endType)
                .runId(runId)
                .message("Run " + finalStatus.name().toLowerCase())
                .timestamp(Instant.now())
                .build());

        AuditAction auditAction = finalStatus == RunStatus.SUCCEEDED ? AuditAction.RUN_COMPLETED : AuditAction.RUN_FAILED;
        auditRepo.save(AuditLog.builder()
                .workflow(run.getWorkflow())
                .workflowRunId(runId)
                .action(auditAction)
                .details("Run finished with status " + finalStatus + " in " + run.getDurationMs() + "ms")
                .build());

        safeMetrics(() -> metricsService.onRunFinished(workerId, runId, message.getWorkflowName(),
                finalStatus == RunStatus.SUCCEEDED, run.getDurationMs()));
    }

    /**
     * Every metrics/ops call in this class is wrapped through here. Instrumentation must
     * NEVER be able to break real execution - if this throws, we log and move on rather
     * than letting it propagate into DagEngine's listener callbacks (which run outside
     * DagEngine's own try/catch for onNodeStarted specifically) or the queue worker loop.
     */
    private void safeMetrics(Runnable action) {
        try {
            action.run();
        } catch (Exception e) {
            log.warn("Queue Ops metrics call failed (execution is unaffected): {}", e.getMessage(), e);
        }
    }

    // ── Queries ────────────────────────────────────────────────────────────────

    @Transactional(readOnly = true)
    public Page<RunResponse> listRuns(UUID workflowId, Pageable pageable) {
        return runRepo.findByWorkflow_IdOrderByCreatedAtDesc(workflowId, pageable).map(this::toRunResponse);
    }

    @Transactional(readOnly = true)
    public RunResponse getRun(UUID runId) {
        return toRunResponse(runRepo.findById(runId)
                .orElseThrow(() -> new RuntimeException("Run not found: " + runId)));
    }

    public List<NodeExecutionResponse> getNodeExecutions(UUID runId) {
        return nodeExecRepo.findByWorkflowRun_IdOrderByStartedAtAsc(runId)
                .stream().map(this::toNodeExecResponse).collect(Collectors.toList());
    }

    @Transactional
    public void cancelRun(UUID runId) {
        WorkflowRun run = runRepo.findById(runId).orElseThrow();
        if (run.getStatus() == RunStatus.RUNNING || run.getStatus() == RunStatus.QUEUED) {
            run.setStatus(RunStatus.CANCELLED);
            run.setFinishedAt(Instant.now());
            runRepo.save(run);
        }
    }

    // ── Approval ───────────────────────────────────────────────────────────────

    @Transactional
    public void approveNode(UUID runId, String clientNodeId, User actor) {
        WorkflowRun run = runRepo.findById(runId).orElseThrow();
        TriggerRunRequest request = new TriggerRunRequest();
        request.setTriggerType(TriggerType.REPLAY);
        request.setReplayFromRunId(runId);
        request.setReplayFromNodeId(clientNodeId);

        Set<String> completed = computeSucceededNodesUpTo(runId, clientNodeId);
        completed.add(clientNodeId); // the approval node itself is now considered done

        List<String> childNodes = edgeRepo.findByWorkflowVersion_Id(run.getWorkflowVersion().getId())
                .stream()
                .filter(e -> e.getSourceClientNodeId().equals(clientNodeId))
                .map(WorkflowEdge::getTargetClientNodeId)
                .collect(Collectors.toList());

        auditRepo.save(AuditLog.builder()
                .actor(actor)
                .workflow(run.getWorkflow())
                .workflowRunId(runId)
                .action(AuditAction.APPROVAL_GRANTED)
                .details("Approval granted for node " + clientNodeId)
                .build());

        ExecutionMessage message = ExecutionMessage.builder()
                .workflowRunId(runId)
                .startNodeIds(childNodes)
                .alreadyCompletedNodeIds(completed)
                .workflowName(run.getWorkflow().getName())
                .build();
        enqueueAfterCommit(message);
    }

    // ── Helpers ────────────────────────────────────────────────────────────────

    private ExecutionListener buildListener(WorkflowRun run, int workerId, String workflowName) {
        UUID runId = run.getId();
        return new ExecutionListener() {
            @Override
            public void onNodeStarted(GraphNodeWrapper node, int attempt) {
                NodeExecution ne = NodeExecution.builder()
                        .workflowRun(run)
                        .clientNodeId(node.getId())
                        .nodeLabel(node.getNode().getLabel())
                        .status(NodeExecutionStatus.RUNNING)
                        .attemptNumber(attempt)
                        .startedAt(Instant.now())
                        .build();
                nodeExecRepo.save(ne);
                broadcaster.broadcast(runId, ExecutionEvent.builder()
                        .type(ExecutionEvent.Type.NODE_STARTED)
                        .runId(runId).nodeId(node.getId()).nodeLabel(node.getNode().getLabel())
                        .nodeStatus(NodeExecutionStatus.RUNNING).attemptNumber(attempt)
                        .timestamp(Instant.now()).build());
                safeMetrics(() -> metricsService.onNodeStarted(workerId, runId, workflowName, node.getNode().getLabel()));
            }

            @Override
            public void onNodeSucceeded(GraphNodeWrapper node, NodeExecutionResult result, int attempt, long durationMs) {
                latestExecution(runId, node.getId()).ifPresent(ne -> {
                    ne.setStatus(NodeExecutionStatus.SUCCEEDED);
                    ne.setOutputJson(toJson(result.getOutput()));
                    ne.setLogOutput(result.getLog());
                    ne.setFinishedAt(Instant.now());
                    ne.setDurationMs(durationMs);
                    nodeExecRepo.save(ne);
                });
                broadcaster.broadcast(runId, ExecutionEvent.builder()
                        .type(ExecutionEvent.Type.NODE_SUCCEEDED)
                        .runId(runId).nodeId(node.getId()).nodeLabel(node.getNode().getLabel())
                        .nodeStatus(NodeExecutionStatus.SUCCEEDED).attemptNumber(attempt)
                        .durationMs(durationMs).output(result.getOutput())
                        .timestamp(Instant.now()).build());
                broadcaster.logLine(runId, node.getId(), result.getLog());
            }

            @Override
            public void onNodeRetrying(GraphNodeWrapper node, int attempt, long nextDelayMs, String error) {
                latestExecution(runId, node.getId()).ifPresent(ne -> {
                    ne.setStatus(NodeExecutionStatus.RETRYING);
                    ne.setErrorMessage(error);
                    nodeExecRepo.save(ne);
                });
                broadcaster.broadcast(runId, ExecutionEvent.builder()
                        .type(ExecutionEvent.Type.NODE_RETRYING)
                        .runId(runId).nodeId(node.getId()).nodeLabel(node.getNode().getLabel())
                        .nodeStatus(NodeExecutionStatus.RETRYING).attemptNumber(attempt)
                        .nextRetryDelayMs(nextDelayMs).errorMessage(error)
                        .timestamp(Instant.now()).build());
                safeMetrics(() -> metricsService.onRetry(workerId, runId, workflowName, node.getNode().getLabel()));
            }

            @Override
            public void onNodeFailedTerminal(GraphNodeWrapper node, String error, int attempt, long durationMs) {
                latestExecution(runId, node.getId()).ifPresent(ne -> {
                    ne.setStatus(NodeExecutionStatus.FAILED);
                    ne.setErrorMessage(error);
                    ne.setFinishedAt(Instant.now());
                    ne.setDurationMs(durationMs);
                    nodeExecRepo.save(ne);
                });
                broadcaster.broadcast(runId, ExecutionEvent.builder()
                        .type(ExecutionEvent.Type.NODE_FAILED)
                        .runId(runId).nodeId(node.getId()).nodeLabel(node.getNode().getLabel())
                        .nodeStatus(NodeExecutionStatus.FAILED).attemptNumber(attempt)
                        .durationMs(durationMs).errorMessage(error)
                        .timestamp(Instant.now()).build());
                safeMetrics(() -> metricsService.onNodeFailed(workerId, runId, workflowName, node.getNode().getLabel()));
            }

            @Override
            public void onNodeSkipped(GraphNodeWrapper node) {
                NodeExecution ne = NodeExecution.builder()
                        .workflowRun(run)
                        .clientNodeId(node.getId())
                        .nodeLabel(node.getNode().getLabel())
                        .status(NodeExecutionStatus.SKIPPED)
                        .startedAt(Instant.now())
                        .finishedAt(Instant.now())
                        .durationMs(0L)
                        .build();
                nodeExecRepo.save(ne);
                broadcaster.broadcast(runId, ExecutionEvent.builder()
                        .type(ExecutionEvent.Type.NODE_SKIPPED)
                        .runId(runId).nodeId(node.getId()).nodeLabel(node.getNode().getLabel())
                        .nodeStatus(NodeExecutionStatus.SKIPPED)
                        .timestamp(Instant.now()).build());
            }

            @Override
            public void onNodeAwaitingApproval(GraphNodeWrapper node, NodeExecutionResult result) {
                latestExecution(runId, node.getId()).ifPresent(ne -> {
                    ne.setStatus(NodeExecutionStatus.AWAITING_APPROVAL);
                    nodeExecRepo.save(ne);
                });
                broadcaster.broadcast(runId, ExecutionEvent.builder()
                        .type(ExecutionEvent.Type.NODE_AWAITING_APPROVAL)
                        .runId(runId).nodeId(node.getId()).nodeLabel(node.getNode().getLabel())
                        .nodeStatus(NodeExecutionStatus.AWAITING_APPROVAL)
                        .message(result.getLog()).timestamp(Instant.now()).build());
            }
        };
    }

    private Optional<NodeExecution> latestExecution(UUID runId, String clientNodeId) {
        return nodeExecRepo.findByWorkflowRun_IdAndClientNodeId(runId, clientNodeId)
                .stream().max(Comparator.comparingInt(NodeExecution::getAttemptNumber));
    }

    private Set<String> computeSucceededNodesUpTo(UUID runId, String untilNodeId) {
        return nodeExecRepo.findByWorkflowRun_IdOrderByStartedAtAsc(runId)
                .stream()
                .filter(ne -> ne.getStatus() == NodeExecutionStatus.SUCCEEDED
                        && !ne.getClientNodeId().equals(untilNodeId))
                .map(NodeExecution::getClientNodeId)
                .collect(Collectors.toSet());
    }

    private List<WorkflowNode> computeRootNodes(UUID versionId) {
        List<WorkflowNode> allNodes = nodeRepo.findByWorkflowVersion_Id(versionId);
        List<WorkflowEdge> allEdges = edgeRepo.findByWorkflowVersion_Id(versionId);
        Set<String> hasParent = allEdges.stream().map(WorkflowEdge::getTargetClientNodeId).collect(Collectors.toSet());
        return allNodes.stream().filter(n -> !hasParent.contains(n.getClientNodeId())).collect(Collectors.toList());
    }

    private void failRun(WorkflowRun run, String message) {
        run.setStatus(RunStatus.FAILED);
        run.setErrorMessage(message);
        run.setFinishedAt(Instant.now());
        runRepo.save(run);
    }

    private RunResponse toRunResponse(WorkflowRun r) {
        return RunResponse.builder()
                .id(r.getId())
                .workflowId(r.getWorkflow().getId())
                .workflowName(r.getWorkflow().getName())
                .versionNumber(r.getWorkflowVersion().getVersionNumber())
                .status(r.getStatus())
                .triggerType(r.getTriggerType())
                .replayedFromRunId(r.getReplayedFromRunId())
                .errorMessage(r.getErrorMessage())
                .startedAt(r.getStartedAt())
                .finishedAt(r.getFinishedAt())
                .durationMs(r.getDurationMs())
                .createdAt(r.getCreatedAt())
                .build();
    }

    private NodeExecutionResponse toNodeExecResponse(NodeExecution ne) {
        return NodeExecutionResponse.builder()
                .id(ne.getId())
                .clientNodeId(ne.getClientNodeId())
                .nodeLabel(ne.getNodeLabel())
                .status(ne.getStatus())
                .attemptNumber(ne.getAttemptNumber())
                .logOutput(ne.getLogOutput())
                .errorMessage(ne.getErrorMessage())
                .startedAt(ne.getStartedAt())
                .finishedAt(ne.getFinishedAt())
                .durationMs(ne.getDurationMs())
                .build();
    }

    private String toJson(Object obj) {
        if (obj == null) return null;
        try { return objectMapper.writeValueAsString(obj); } catch (Exception e) { return null; }
    }

    @SuppressWarnings("unchecked")
    private Map<String, Object> fromJson(String json) {
        if (json == null || json.isBlank()) return new HashMap<>();
        try { return objectMapper.readValue(json, new TypeReference<>() {}); } catch (Exception e) { return new HashMap<>(); }
    }
    
    private void audit(User actor, Workflow workflow, UUID runId, AuditAction action, String details) {
        auditRepo.save(AuditLog.builder()
                .actor(actor).workflow(workflow).workflowRunId(runId)
                .action(action).details(details).build());
    }
}