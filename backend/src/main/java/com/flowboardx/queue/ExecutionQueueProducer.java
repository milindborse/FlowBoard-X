package com.flowboardx.queue;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.flowboardx.ops.QueueMetricsService;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Component;

@Slf4j
@Component
public class ExecutionQueueProducer {

    private final StringRedisTemplate redisTemplate;
    private final QueueMetricsService metricsService;
    private final ObjectMapper objectMapper = new ObjectMapper();

    @Value("${flowboardx.execution.queue-key}")
    private String queueKey;

    public ExecutionQueueProducer(StringRedisTemplate redisTemplate, QueueMetricsService metricsService) {
        this.redisTemplate = redisTemplate;
        this.metricsService = metricsService;
    }

    public void enqueue(ExecutionMessage message) {
        try {
            // Stamp enqueue time if not already set - powers the Queue Ops Dashboard's
            // wait-time metric. Purely additive; execution semantics are unaffected.
            if (message.getEnqueuedAtEpochMs() == null) {
                message.setEnqueuedAtEpochMs(System.currentTimeMillis());
            }
            String payload = objectMapper.writeValueAsString(message);
            redisTemplate.opsForList().leftPush(queueKey, payload);
            log.info("Enqueued execution for run {}", message.getWorkflowRunId());
            try {
                metricsService.onEnqueue(message.getWorkflowRunId(), message.getWorkflowName());
            } catch (Exception metricsEx) {
                log.warn("Queue-metrics hook failed on enqueue (message still enqueued): {}", metricsEx.getMessage());
            }
        } catch (Exception e) {
            throw new RuntimeException("Failed to enqueue execution message", e);
        }
    }

    public long queueDepth() {
        Long size = redisTemplate.opsForList().size(queueKey);
        return size == null ? 0 : size;
    }
}