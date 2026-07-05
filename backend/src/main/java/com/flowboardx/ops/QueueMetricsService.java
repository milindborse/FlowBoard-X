package com.flowboardx.ops;

import com.flowboardx.dto.QueueActivityEventResponse;
import com.flowboardx.dto.QueueMetricsResponse;
import com.flowboardx.dto.WorkerStatusResponse;
import lombok.RequiredArgsConstructor;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Service;

import java.time.Instant;
import java.util.*;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentLinkedDeque;
import java.util.concurrent.atomic.AtomicLong;
import java.util.UUID;
import java.util.stream.Collectors;

/**
 * In-memory tracker for distributed-worker/queue operational metrics. Deliberately NOT persisted
 * to Postgres (resets on restart) - this is live operational telemetry, not business data, and
 * adding a metrics table would mean a schema migration for something that's inherently transient.
 *
 * Every mutation method here is called ADDITIVELY from existing execution code paths
 * (ExecutionQueueProducer, ExecutionQueueWorker, ExecutionService's listener callbacks) -
 * nothing in this class changes any execution/business decision, it only observes and records.
 */
@Service
@RequiredArgsConstructor
public class QueueMetricsService {

    private static final int TREND_HISTORY_SIZE = 120;   // ~10 min at 5s sampling
    private static final int ACTIVITY_FEED_SIZE = 200;
    private static final int WAIT_SAMPLE_SIZE = 100;
    private static final long WINDOW_MS = 60_000L;

    private final StringRedisTemplate redisTemplate;
    private final OpsBroadcaster broadcaster;

    @Value("${flowboardx.execution.queue-key}")
    private String queueKey;

    private long queueDepth() {
        Long size = redisTemplate.opsForList().size(queueKey);
        return size == null ? 0 : size;
    }

    @Value("${flowboardx.execution.worker-pool-size:4}")
    private int totalWorkers;

    private final Map<Integer, WorkerState> workers = new ConcurrentHashMap<>();

    private final AtomicLong totalEnqueued = new AtomicLong();
    private final AtomicLong totalDequeued = new AtomicLong();
    private final AtomicLong totalRetries = new AtomicLong();
    private final AtomicLong totalJobsProcessed = new AtomicLong();
    private volatile long peakQueueLength = 0;

    // Rolling windows for rate calculations (timestamps only, epoch millis)
    private final Deque<Long> enqueueTimestamps = new ConcurrentLinkedDeque<>();
    private final Deque<Long> dequeueTimestamps = new ConcurrentLinkedDeque<>();
    private final Deque<Long> recentWaitMs = new ConcurrentLinkedDeque<>();

    private final Deque<Map<String, Object>> queueLengthTrend = new ConcurrentLinkedDeque<>();
    private final Deque<Map<String, Object>> workerUtilizationTrend = new ConcurrentLinkedDeque<>();
    private final Deque<Map<String, Object>> queueThroughputTrend = new ConcurrentLinkedDeque<>();
    private final Deque<Map<String, Object>> executionLatencyTrend = new ConcurrentLinkedDeque<>();
    private final Deque<Map<String, Object>> queueWaitTimeTrend = new ConcurrentLinkedDeque<>();

    private final Deque<QueueActivityEventResponse> activityFeed = new ConcurrentLinkedDeque<>();

    private WorkerState worker(int workerId) {
        return workers.computeIfAbsent(workerId, WorkerState::new);
    }

    // ── Producer hook ────────────────────────────────────────────────────────

    public void onEnqueue(UUID runId, String workflowName) {
        totalEnqueued.incrementAndGet();
        long now = System.currentTimeMillis();
        enqueueTimestamps.addLast(now);
        trimWindow(enqueueTimestamps, now);
        long depth = queueDepth();
        if (depth > peakQueueLength) peakQueueLength = depth;
        recordActivity("ENQUEUED", runId, workflowName, null, null, "Run queued for execution");
    }

    // ── Worker hooks ─────────────────────────────────────────────────────────

    public void onDequeue(int workerId, UUID runId, String workflowName, long enqueuedAtEpochMs) {
        totalDequeued.incrementAndGet();
        long now = System.currentTimeMillis();
        dequeueTimestamps.addLast(now);
        trimWindow(dequeueTimestamps, now);

        long waitMs = Math.max(0, now - enqueuedAtEpochMs);
        recentWaitMs.addLast(waitMs);
        while (recentWaitMs.size() > WAIT_SAMPLE_SIZE) recentWaitMs.pollFirst();

        worker(workerId).markActive(runId, workflowName);
        recordActivity("DEQUEUED", runId, workflowName, null, workerId, "Worker-" + workerId + " picked up run");
    }

    public void onNodeStarted(int workerId, UUID runId, String workflowName, String nodeLabel) {
        worker(workerId).updateCurrentNode(nodeLabel);
        recordActivity("NODE_STARTED", runId, workflowName, nodeLabel, workerId, "Executing \"" + nodeLabel + "\"");
    }

    public void onRetry(int workerId, UUID runId, String workflowName, String nodeLabel) {
        totalRetries.incrementAndGet();
        recordActivity("RETRY", runId, workflowName, nodeLabel, workerId, "Retrying \"" + nodeLabel + "\"");
    }

    public void onNodeFailed(int workerId, UUID runId, String workflowName, String nodeLabel) {
        recordActivity("NODE_FAILED", runId, workflowName, nodeLabel, workerId, "\"" + nodeLabel + "\" failed");
    }

    /** Called once per run, when the worker finishes processing it (success or failure). */
    public void onRunFinished(int workerId, UUID runId, String workflowName, boolean succeeded, long durationMs) {
        totalJobsProcessed.incrementAndGet();
        worker(workerId).markIdle(succeeded);
        recordActivity(succeeded ? "RUN_COMPLETED" : "RUN_FAILED", runId, workflowName, null, workerId,
                "Run " + (succeeded ? "completed" : "failed") + " in " + durationMs + "ms");

        Map<String, Object> point = new LinkedHashMap<>();
        point.put("timestamp", Instant.now().toString());
        point.put("value", durationMs);
        pushBounded(executionLatencyTrend, point);
    }

    // ── Periodic sampling (called by scheduled ticker) ──────────────────────

    public void sampleTrends() {
        long depth = queueDepth();
        if (depth > peakQueueLength) peakQueueLength = depth;
        String ts = Instant.now().toString();

        pushBounded(queueLengthTrend, point(ts, depth));
        pushBounded(workerUtilizationTrend, point(ts, workerUtilizationPercent()));
        pushBounded(queueThroughputTrend, point(ts, throughputPerMinute()));
        pushBounded(queueWaitTimeTrend, point(ts, avgWaitMs() == null ? 0 : avgWaitMs()));
    }

    private Map<String, Object> point(String ts, Object value) {
        Map<String, Object> m = new LinkedHashMap<>();
        m.put("timestamp", ts);
        m.put("value", value);
        return m;
    }

    private void pushBounded(Deque<Map<String, Object>> deque, Map<String, Object> point) {
        deque.addLast(point);
        while (deque.size() > TREND_HISTORY_SIZE) deque.pollFirst();
    }

    private void recordActivity(String type, UUID runId, String workflowName, String nodeLabel, Integer workerId, String message) {
        QueueActivityEventResponse event = QueueActivityEventResponse.builder()
                .type(type).timestamp(Instant.now()).runId(runId).workflowName(workflowName)
                .nodeLabel(nodeLabel).workerId(workerId).message(message).build();
        activityFeed.addLast(event);
        while (activityFeed.size() > ACTIVITY_FEED_SIZE) activityFeed.pollFirst();
        broadcaster.broadcastActivity(event);
    }

    private void trimWindow(Deque<Long> timestamps, long now) {
        while (!timestamps.isEmpty() && now - timestamps.peekFirst() > WINDOW_MS) {
            timestamps.pollFirst();
        }
    }

    // ── Derived values ───────────────────────────────────────────────────────

    private double throughputPerMinute() {
        trimWindow(dequeueTimestamps, System.currentTimeMillis());
        return dequeueTimestamps.size(); // count in the trailing 60s window == per-minute rate
    }

    /** Positive = queue shrinking (draining faster than it's filling); negative = queue growing. */
    private double drainRatePerMinute() {
        trimWindow(enqueueTimestamps, System.currentTimeMillis());
        trimWindow(dequeueTimestamps, System.currentTimeMillis());
        return dequeueTimestamps.size() - enqueueTimestamps.size();
    }

    private Long avgWaitMs() {
        if (recentWaitMs.isEmpty()) return null;
        return (long) recentWaitMs.stream().mapToLong(Long::longValue).average().orElse(0);
    }

    private double workerUtilizationPercent() {
        if (totalWorkers == 0) return 0;
        long active = workers.values().stream().filter(w -> w.getStatus() == WorkerState.Status.ACTIVE).count();
        return (active * 100.0) / totalWorkers;
    }

    // ── Public reads ─────────────────────────────────────────────────────────

    public List<QueueActivityEventResponse> recentActivity(int limit) {
        List<QueueActivityEventResponse> all = new ArrayList<>(activityFeed);
        Collections.reverse(all);
        return all.stream().limit(limit).collect(Collectors.toList());
    }

    public QueueMetricsResponse snapshot() {
        long active = workers.values().stream().filter(w -> w.getStatus() == WorkerState.Status.ACTIVE).count();
        long idle = totalWorkers - active;

        List<WorkerStatusResponse> workerDtos = new ArrayList<>();
        for (int i = 0; i < totalWorkers; i++) {
            WorkerState w = worker(i);
            workerDtos.add(WorkerStatusResponse.builder()
                    .workerId(w.getWorkerId())
                    .status(w.getStatus().name())
                    .currentRunId(w.getCurrentRunId())
                    .currentWorkflowName(w.getCurrentWorkflowName())
                    .currentNodeLabel(w.getCurrentNodeLabel())
                    .completedJobs(w.getCompletedJobs())
                    .failedJobs(w.getFailedJobs())
                    .lastHeartbeat(w.getLastHeartbeat())
                    .currentDurationMs(w.currentDurationMs())
                    .build());
        }

        return QueueMetricsResponse.builder()
                .queueLength(queueDepth())
                .peakQueueLength(peakQueueLength)
                .queueThroughputPerMinute(throughputPerMinute())
                .avgQueueWaitMs(avgWaitMs())
                .workerUtilizationPercent(workerUtilizationPercent())
                .activeWorkers((int) active)
                .idleWorkers((int) idle)
                .totalWorkers(totalWorkers)
                .retryCount(totalRetries.get())
                .queueDrainRatePerMinute(drainRatePerMinute())
                .totalJobsProcessed(totalJobsProcessed.get())
                .workers(workerDtos)
                .queueLengthTrend(new ArrayList<>(queueLengthTrend))
                .workerUtilizationTrend(new ArrayList<>(workerUtilizationTrend))
                .queueThroughputTrend(new ArrayList<>(queueThroughputTrend))
                .executionLatencyTrend(new ArrayList<>(executionLatencyTrend))
                .queueWaitTimeTrend(new ArrayList<>(queueWaitTimeTrend))
                .build();
    }

    /** Reset Metrics toolbar action - clears counters/trends/feed, does NOT touch live worker state or the actual Redis queue. */
    public void reset() {
        totalEnqueued.set(0);
        totalDequeued.set(0);
        totalRetries.set(0);
        totalJobsProcessed.set(0);
        peakQueueLength = queueDepth();
        enqueueTimestamps.clear();
        dequeueTimestamps.clear();
        recentWaitMs.clear();
        queueLengthTrend.clear();
        workerUtilizationTrend.clear();
        queueThroughputTrend.clear();
        executionLatencyTrend.clear();
        queueWaitTimeTrend.clear();
        activityFeed.clear();
    }
}