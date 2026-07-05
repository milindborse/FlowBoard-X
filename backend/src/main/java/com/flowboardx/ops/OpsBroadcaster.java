package com.flowboardx.ops;

import com.flowboardx.dto.QueueActivityEventResponse;
import com.flowboardx.dto.QueueMetricsResponse;
import lombok.extern.slf4j.Slf4j;
import org.springframework.messaging.simp.SimpMessagingTemplate;
import org.springframework.stereotype.Component;

/**
 * Broadcasts queue/worker operational data over WebSocket, separate from the existing
 * per-run /topic/runs/{runId} topic used by RunViewer - keeps this new feature fully
 * isolated from the execution-viewer's existing WS traffic.
 *
 * Topics:
 *   /topic/ops/queue    - full QueueMetricsResponse snapshot, pushed periodically
 *   /topic/ops/activity - one QueueActivityEventResponse per real-time event
 */
@Slf4j
@Component
public class OpsBroadcaster {

    private final SimpMessagingTemplate messagingTemplate;

    public OpsBroadcaster(SimpMessagingTemplate messagingTemplate) {
        this.messagingTemplate = messagingTemplate;
    }

    public void broadcastMetrics(QueueMetricsResponse snapshot) {
        try {
            messagingTemplate.convertAndSend("/topic/ops/queue", snapshot);
        } catch (Exception e) {
            log.warn("Failed to broadcast ops metrics: {}", e.getMessage());
        }
    }

    public void broadcastActivity(QueueActivityEventResponse event) {
        try {
            messagingTemplate.convertAndSend("/topic/ops/activity", event);
        } catch (Exception e) {
            log.warn("Failed to broadcast ops activity: {}", e.getMessage());
        }
    }
}