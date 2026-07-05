package com.flowboardx.ops;

import lombok.RequiredArgsConstructor;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

@Component
@RequiredArgsConstructor
public class OpsMetricsScheduler {

    private final QueueMetricsService metricsService;
    private final OpsBroadcaster broadcaster;

    /** Powers the trend charts (Queue Length, Worker Utilization, Throughput, Queue Wait Time). */
    @Scheduled(fixedRate = 5000)
    public void sampleTrends() {
        metricsService.sampleTrends();
    }

    /** Keeps the Dashboard's live queue/worker panel updated without the client polling. */
    @Scheduled(fixedRate = 2000)
    public void broadcastSnapshot() {
        broadcaster.broadcastMetrics(metricsService.snapshot());
    }
}