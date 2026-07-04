package com.flowboardx.engine;

/**
 * Callback hooks the DagEngine fires as nodes move through their lifecycle.
 * Implemented by ExecutionService, which persists NodeExecution rows and
 * broadcasts the same events over WebSocket for the live execution viewer -
 * the engine itself has zero knowledge of JPA or WebSockets.
 */
public interface ExecutionListener {
    void onNodeStarted(GraphNodeWrapper node, int attemptNumber);

    void onNodeSucceeded(GraphNodeWrapper node, NodeExecutionResult result, int attemptNumber, long durationMs);

    void onNodeRetrying(GraphNodeWrapper node, int attemptNumber, long nextDelayMs, String error);

    void onNodeFailedTerminal(GraphNodeWrapper node, String error, int attemptNumber, long durationMs);

    void onNodeSkipped(GraphNodeWrapper node);

    void onNodeAwaitingApproval(GraphNodeWrapper node, NodeExecutionResult result);
}
