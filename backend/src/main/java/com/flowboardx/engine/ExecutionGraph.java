package com.flowboardx.engine;

import com.flowboardx.domain.entity.WorkflowEdge;
import com.flowboardx.domain.entity.WorkflowNode;

import java.util.*;

/**
 * Builds an in-memory directed graph from persisted nodes/edges and provides
 * the two core structural guarantees the engine depends on:
 *
 *   1. Cycle detection (a workflow MUST be a DAG - cycles are rejected at
 *      save-time AND defensively re-checked at execution-time).
 *   2. Topological ordering (used only for validation / visualization;
 *      actual execution is event-driven off live in-degree counters so
 *      independent branches can run truly concurrently rather than in
 *      strict layer-by-layer lockstep).
 */
public class ExecutionGraph {

    private final Map<String, GraphNodeWrapper> nodesById = new LinkedHashMap<>();
    private final List<GraphEdgeWrapper> edges = new ArrayList<>();

    public ExecutionGraph(List<WorkflowNode> nodes, List<WorkflowEdge> workflowEdges) {
        for (WorkflowNode node : nodes) {
            nodesById.put(node.getClientNodeId(), new GraphNodeWrapper(node));
        }
        for (WorkflowEdge edge : workflowEdges) {
            GraphNodeWrapper source = nodesById.get(edge.getSourceClientNodeId());
            GraphNodeWrapper target = nodesById.get(edge.getTargetClientNodeId());
            if (source == null || target == null) {
                throw new IllegalStateException(
                        "Edge references unknown node: " + edge.getSourceClientNodeId() + " -> " + edge.getTargetClientNodeId());
            }
            GraphEdgeWrapper wrapped = new GraphEdgeWrapper(source, target, edge.getBranchLabel());
            source.getOutgoing().add(wrapped);
            target.getIncoming().add(wrapped);
            target.getRemainingDependencies().incrementAndGet();
            edges.add(wrapped);
        }
        detectCycles();
    }

    public Collection<GraphNodeWrapper> allNodes() {
        return nodesById.values();
    }

    public GraphNodeWrapper get(String clientNodeId) {
        return nodesById.get(clientNodeId);
    }

    public List<GraphNodeWrapper> rootNodes() {
        List<GraphNodeWrapper> roots = new ArrayList<>();
        for (GraphNodeWrapper n : nodesById.values()) {
            if (n.getIncoming().isEmpty()) roots.add(n);
        }
        return roots;
    }

    /** Kahn's algorithm - also doubles as a structural sanity check. */
    public List<String> topologicalOrder() {
        Map<String, Integer> indegree = new HashMap<>();
        for (GraphNodeWrapper n : nodesById.values()) {
            indegree.put(n.getId(), n.getIncoming().size());
        }
        Deque<String> queue = new ArrayDeque<>();
        for (Map.Entry<String, Integer> e : indegree.entrySet()) {
            if (e.getValue() == 0) queue.add(e.getKey());
        }
        List<String> order = new ArrayList<>();
        while (!queue.isEmpty()) {
            String id = queue.poll();
            order.add(id);
            for (GraphEdgeWrapper edge : nodesById.get(id).getOutgoing()) {
                String targetId = edge.getTarget().getId();
                int remaining = indegree.merge(targetId, -1, Integer::sum);
                if (remaining == 0) queue.add(targetId);
            }
        }
        if (order.size() != nodesById.size()) {
            throw new CycleDetectedException("Workflow graph contains a cycle - cannot produce a valid execution order");
        }
        return order;
    }

    /** DFS-based three-color cycle detection, run eagerly at construction time. */
    private void detectCycles() {
        Map<String, Integer> color = new HashMap<>(); // 0=white,1=gray,2=black
        for (String id : nodesById.keySet()) color.put(id, 0);
        for (String id : nodesById.keySet()) {
            if (color.get(id) == 0) {
                dfsVisit(id, color, new ArrayDeque<>());
            }
        }
    }

    private void dfsVisit(String id, Map<String, Integer> color, Deque<String> stack) {
        color.put(id, 1);
        stack.push(id);
        for (GraphEdgeWrapper edge : nodesById.get(id).getOutgoing()) {
            String targetId = edge.getTarget().getId();
            int targetColor = color.get(targetId);
            if (targetColor == 1) {
                stack.push(targetId);
                throw new CycleDetectedException(
                        "Cycle detected in workflow graph: " + String.join(" -> ", new ArrayList<>(stack)));
            } else if (targetColor == 0) {
                dfsVisit(targetId, color, stack);
            }
        }
        stack.pop();
        color.put(id, 2);
    }
}
