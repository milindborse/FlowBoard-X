package com.flowboardx.dto;

import lombok.Builder;
import lombok.Getter;

import java.util.List;
import java.util.Map;

@Getter
@Builder
public class QueueMetricsResponse {
    // Live snapshot values
    private long queueLength;
    private long peakQueueLength;
    private double queueThroughputPerMinute;
    private Long avgQueueWaitMs;
    private double workerUtilizationPercent;
    private int activeWorkers;
    private int idleWorkers;
    private int totalWorkers;
    private long retryCount;
    private double queueDrainRatePerMinute;
    private long totalJobsProcessed;

    private List<WorkerStatusResponse> workers;

    // Trend series - each point is {"timestamp": ISO string, "value": number}
    private List<Map<String, Object>> queueLengthTrend;
    private List<Map<String, Object>> workerUtilizationTrend;
    private List<Map<String, Object>> queueThroughputTrend;
    private List<Map<String, Object>> executionLatencyTrend;
    private List<Map<String, Object>> queueWaitTimeTrend;
}