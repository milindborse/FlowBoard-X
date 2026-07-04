package com.flowboardx.engine;

import com.flowboardx.domain.entity.WorkflowNode;
import lombok.Getter;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.atomic.AtomicInteger;

/**
 * In-memory wrapper around a persisted WorkflowNode used purely during
 * execution. Tracks live in-degree so the engine knows the instant a
 * node becomes "ready" (all of its dependencies have resolved).
 */
@Getter
public class GraphNodeWrapper {
    private final WorkflowNode node;
    private final List<GraphEdgeWrapper> outgoing = new ArrayList<>();
    private final List<GraphEdgeWrapper> incoming = new ArrayList<>();
    private final AtomicInteger remainingDependencies = new AtomicInteger(0);

    public GraphNodeWrapper(WorkflowNode node) {
        this.node = node;
    }

    public String getId() {
        return node.getClientNodeId();
    }
}
