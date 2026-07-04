package com.flowboardx.engine;

import lombok.Getter;

import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Shared, thread-safe state passed to every node executor for the lifetime
 * of a single workflow run. Holds upstream outputs keyed by clientNodeId so
 * downstream nodes (Transform, Aggregator, Merge...) can reference any
 * ancestor's result, not just their immediate parent.
 */
@Getter
public class ExecutionContext {
    private final UUID workflowRunId;
    private final Map<String, Object> triggerPayload;
    private final ConcurrentHashMap<String, Map<String, Object>> nodeOutputs = new ConcurrentHashMap<>();
    private final ConcurrentHashMap<String, RetryOverride> retryOverrides = new ConcurrentHashMap<>();

    public ExecutionContext(UUID workflowRunId, Map<String, Object> triggerPayload) {
        this.workflowRunId = workflowRunId;
        this.triggerPayload = triggerPayload;
    }

    public void recordOutput(String clientNodeId, Map<String, Object> output) {
        nodeOutputs.put(clientNodeId, output == null ? Map.of() : output);
    }

    public Map<String, Object> getOutputOf(String clientNodeId) {
        return nodeOutputs.getOrDefault(clientNodeId, Map.of());
    }

    /** Merges outputs of every direct upstream node into a single flat map (last-write-wins on key clashes). */
    public Map<String, Object> mergedUpstreamOutput(GraphNodeWrapper node) {
        Map<String, Object> merged = new ConcurrentHashMap<>();
        if (node.getIncoming().isEmpty()) {
            merged.putAll(triggerPayload == null ? Map.of() : triggerPayload);
        }
        for (GraphEdgeWrapper edge : node.getIncoming()) {
            merged.putAll(getOutputOf(edge.getSource().getId()));
        }
        return merged;
    }

    public record RetryOverride(int maxAttempts, long baseBackoffMs) {}
}
