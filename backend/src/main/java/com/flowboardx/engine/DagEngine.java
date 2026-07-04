package com.flowboardx.engine;

import com.flowboardx.domain.enums.RunStatus;
import com.flowboardx.engine.executor.NodeExecutor;
import com.flowboardx.engine.executor.NodeExecutorRegistry;
import com.flowboardx.retry.BackoffCalculator;
import com.flowboardx.retry.RetryPolicy;
import org.springframework.stereotype.Component;

import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.*;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicInteger;

/**
 * The core execution engine. Given a validated ExecutionGraph, runs every
 * node exactly once (success, terminal failure, or skip) by reacting to
 * in-degree counters reaching zero, rather than processing the graph in
 * rigid layers - this is what allows independent branches to run truly
 * concurrently while a node with multiple incoming branches waits for all
 * of them exactly once, however many ancestors it has.
 *
 * Supports three entry shapes through the same code path:
 *   - Fresh run:        alreadyCompletedIds = {}, startNodeIds = all roots
 *   - Replay-from-node: alreadyCompletedIds = previously SUCCEEDED nodes,
 *                        startNodeIds = {the failed node}
 *   - Approval resume:  alreadyCompletedIds = everything up to and
 *                        including the approval node, startNodeIds =
 *                        the approval node's direct children
 */
@Component
public class DagEngine {

    public RunStatus execute(
            ExecutionGraph graph,
            ExecutionContext context,
            NodeExecutorRegistry registry,
            ExecutionListener listener,
            Set<String> alreadyCompletedIds,
            List<String> startNodeIds,
            int workerPoolSize,
            int defaultMaxRetryAttempts,
            long defaultBaseBackoffMs
    ) {
        long toProcess = graph.allNodes().stream().filter(n -> !alreadyCompletedIds.contains(n.getId())).count();
        if (toProcess == 0) return RunStatus.SUCCEEDED;

        RunState state = new RunState(graph, context, registry, listener, (int) toProcess,
                defaultMaxRetryAttempts, defaultBaseBackoffMs, Executors.newFixedThreadPool(Math.max(1, workerPoolSize)));

        try {
            // Replay historical edges out of already-completed nodes so dependents'
            // in-degree counters reflect reality before anything new gets dispatched.
            for (String completedId : alreadyCompletedIds) {
                GraphNodeWrapper completedNode = graph.get(completedId);
                if (completedNode == null) continue;
                Map<String, Object> output = context.getOutputOf(completedId);
                String branch = output.containsKey("result")
                        ? (Boolean.TRUE.equals(output.get("result")) ? "true" : "false")
                        : null;
                for (GraphEdgeWrapper edge : completedNode.getOutgoing()) {
                    boolean passed = edge.getBranchLabel() == null || edge.getBranchLabel().equals(branch);
                    resolveEdge(edge, passed, state, false);
                }
            }

            // Kick off the requested entry points: all roots on a fresh run, or
            // the single failed/approved node on a replay.
            for (String startId : startNodeIds) {
                GraphNodeWrapper startNode = graph.get(startId);
                if (startNode != null) dispatch(startNode, state);
            }

            state.done.get(30, TimeUnit.MINUTES);
        } catch (Exception e) {
            state.anyFailed.set(true);
        } finally {
            state.pool.shutdown();
        }

        if (state.awaitingApproval.get()) return RunStatus.AWAITING_APPROVAL;
        return state.anyFailed.get() ? RunStatus.FAILED : RunStatus.SUCCEEDED;
    }

    private void dispatch(GraphNodeWrapper node, RunState state) {
        if (!state.dispatched.add(node.getId())) return; // already running or finished
        state.pool.submit(() -> runWithRetry(node, state));
    }

    private void runWithRetry(GraphNodeWrapper node, RunState state) {
        NodeExecutor executor = state.registry.get(node.getNode().getType());
        RetryPolicy policy = resolveRetryPolicy(node, state);

        String lastError = null;
        for (int attempt = 1; attempt <= policy.maxAttempts(); attempt++) {
            if (attempt > 1) {
                long delay = BackoffCalculator.delayForAttempt(policy, attempt);
                state.listener.onNodeRetrying(node, attempt, delay, lastError);
                sleepQuietly(delay);
            }
            long start = System.currentTimeMillis();
            state.listener.onNodeStarted(node, attempt);

            try {
                Map<String, Object> input = state.context.mergedUpstreamOutput(node);
                NodeExecutionResult result = executor.execute(node.getNode(), input, state.context);
                long durationMs = System.currentTimeMillis() - start;

                if (result.getStatus() == com.flowboardx.domain.enums.NodeExecutionStatus.SUCCEEDED) {
                    state.context.recordOutput(node.getId(), result.getOutput());
                    state.listener.onNodeSucceeded(node, result, attempt, durationMs);
                    finalizeAndCascade(node, true, result.getConditionBranch(), state);
                    return;
                }
                if (result.getStatus() == com.flowboardx.domain.enums.NodeExecutionStatus.AWAITING_APPROVAL) {
                    state.context.recordOutput(node.getId(), result.getOutput());
                    state.listener.onNodeAwaitingApproval(node, result);
                    state.awaitingApproval.set(true);
                    state.decrementRemaining();
                    return;
                }
                // Anything else is a failed attempt.
                lastError = result.getErrorMessage();
                if (attempt >= policy.maxAttempts()) {
                    state.listener.onNodeFailedTerminal(node, lastError, attempt, durationMs);
                    state.anyFailed.set(true);
                    finalizeAndCascade(node, false, null, state);
                    return;
                }
            } catch (Exception e) {
                lastError = e.getMessage();
                long durationMs = System.currentTimeMillis() - start;
                if (attempt >= policy.maxAttempts()) {
                    state.listener.onNodeFailedTerminal(node, lastError, attempt, durationMs);
                    state.anyFailed.set(true);
                    finalizeAndCascade(node, false, null, state);
                    return;
                }
            }
        }
    }

    /** Called exactly once per node, the instant its own outcome is final - decrements the run counter and fans out. */
    private void finalizeAndCascade(GraphNodeWrapper node, boolean succeeded, String conditionBranch, RunState state) {
        state.decrementRemaining();
        for (GraphEdgeWrapper edge : node.getOutgoing()) {
            boolean passed = succeeded && (edge.getBranchLabel() == null || edge.getBranchLabel().equals(conditionBranch));
            resolveEdge(edge, passed, state, true);
        }
    }

    /**
     * Resolves a single edge. If it brings the target's in-degree to zero, the
     * target either dispatches (at least one incoming path actually delivered
     * data) or skip-cascades (every incoming path was skipped/failed/condition-rejected).
     */
    private void resolveEdge(GraphEdgeWrapper edge, boolean passed, RunState state, boolean async) {
        edge.setResolution(passed);
        GraphNodeWrapper target = edge.getTarget();
        if (target.getRemainingDependencies().decrementAndGet() > 0) return;

        boolean anyIncomingPassed = target.getIncoming().stream().anyMatch(e -> Boolean.TRUE.equals(e.getResolution()));
        if (anyIncomingPassed) {
            if (async) dispatch(target, state);
            else {
                // Pre-resolution pass runs synchronously on the calling thread - cheap, no business logic yet.
                if (state.dispatched.add(target.getId())) state.pool.submit(() -> runWithRetry(target, state));
            }
        } else {
            state.listener.onNodeSkipped(target);
            finalizeAndCascade(target, false, null, state);
        }
    }

    private RetryPolicy resolveRetryPolicy(GraphNodeWrapper node, RunState state) {
        if (node.getNode().getRetryMaxAttempts() != null) {
            long backoff = node.getNode().getRetryBaseBackoffMs() != null
                    ? node.getNode().getRetryBaseBackoffMs() : state.defaultBaseBackoffMs;
            return new RetryPolicy(node.getNode().getRetryMaxAttempts(), backoff);
        }
        for (GraphEdgeWrapper edge : node.getIncoming()) {
            ExecutionContext.RetryOverride override = state.context.getRetryOverrides().get(edge.getSource().getId());
            if (override != null) return new RetryPolicy(override.maxAttempts(), override.baseBackoffMs());
        }
        return new RetryPolicy(state.defaultMaxRetryAttempts, state.defaultBaseBackoffMs);
    }

    private void sleepQuietly(long ms) {
        if (ms <= 0) return;
        try {
            Thread.sleep(ms);
        } catch (InterruptedException ignored) {
            Thread.currentThread().interrupt();
        }
    }

    /** Bundles every piece of mutable state shared across a single run so methods don't drown in parameters. */
    private static final class RunState {
        final ExecutionGraph graph;
        final ExecutionContext context;
        final NodeExecutorRegistry registry;
        final ExecutionListener listener;
        final int defaultMaxRetryAttempts;
        final long defaultBaseBackoffMs;
        final ExecutorService pool;
        final AtomicInteger remaining;
        final AtomicBoolean anyFailed = new AtomicBoolean(false);
        final AtomicBoolean awaitingApproval = new AtomicBoolean(false);
        final Set<String> dispatched = ConcurrentHashMap.newKeySet();
        final CompletableFuture<Void> done = new CompletableFuture<>();

        RunState(ExecutionGraph graph, ExecutionContext context, NodeExecutorRegistry registry,
                  ExecutionListener listener, int totalToProcess, int defaultMaxRetryAttempts,
                  long defaultBaseBackoffMs, ExecutorService pool) {
            this.graph = graph;
            this.context = context;
            this.registry = registry;
            this.listener = listener;
            this.defaultMaxRetryAttempts = defaultMaxRetryAttempts;
            this.defaultBaseBackoffMs = defaultBaseBackoffMs;
            this.pool = pool;
            this.remaining = new AtomicInteger(totalToProcess);
        }

        void decrementRemaining() {
            if (remaining.decrementAndGet() <= 0) done.complete(null);
        }
    }
}
