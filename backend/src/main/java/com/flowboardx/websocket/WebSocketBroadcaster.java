package com.flowboardx.websocket;

import lombok.extern.slf4j.Slf4j;
import org.springframework.messaging.simp.SimpMessagingTemplate;
import org.springframework.stereotype.Component;

import java.util.UUID;

/**
 * Thin wrapper around SimpMessagingTemplate that routes each event to the
 * run-specific STOMP topic the frontend subscribes to.
 *
 * Topic pattern: /topic/runs/{runId}
 * Client subscribes: stompClient.subscribe('/topic/runs/' + runId, handler)
 */
@Slf4j
@Component
public class WebSocketBroadcaster {

    private final SimpMessagingTemplate messagingTemplate;

    public WebSocketBroadcaster(SimpMessagingTemplate messagingTemplate) {
        this.messagingTemplate = messagingTemplate;
    }

    public void broadcast(UUID runId, ExecutionEvent event) {
        String destination = "/topic/runs/" + runId;
        try {
            messagingTemplate.convertAndSend(destination, event);
        } catch (Exception e) {
            log.warn("Failed to broadcast event {} to {}: {}", event.getType(), destination, e.getMessage());
        }
    }

    public void logLine(UUID runId, String nodeId, String message) {
        broadcast(runId, ExecutionEvent.builder()
                .type(ExecutionEvent.Type.LOG_LINE)
                .runId(runId)
                .nodeId(nodeId)
                .message(message)
                .timestamp(java.time.Instant.now())
                .build());
    }
}
