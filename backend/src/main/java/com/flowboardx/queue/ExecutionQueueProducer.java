package com.flowboardx.queue;

import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Component;

@Slf4j
@Component
public class ExecutionQueueProducer {

    private final StringRedisTemplate redisTemplate;
    private final ObjectMapper objectMapper = new ObjectMapper();

    @Value("${flowboardx.execution.queue-key}")
    private String queueKey;

    public ExecutionQueueProducer(StringRedisTemplate redisTemplate) {
        this.redisTemplate = redisTemplate;
    }

    public void enqueue(ExecutionMessage message) {
        try {
            String payload = objectMapper.writeValueAsString(message);
            redisTemplate.opsForList().leftPush(queueKey, payload);
            log.info("Enqueued execution for run {}", message.getWorkflowRunId());
        } catch (Exception e) {
            throw new RuntimeException("Failed to enqueue execution message", e);
        }
    }

    public long queueDepth() {
        Long size = redisTemplate.opsForList().size(queueKey);
        return size == null ? 0 : size;
    }
}
