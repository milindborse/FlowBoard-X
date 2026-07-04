package com.flowboardx.queue;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.flowboardx.service.ExecutionService;
import jakarta.annotation.PostConstruct;
import jakarta.annotation.PreDestroy;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Component;

import java.time.Duration;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

/**
 * Models the "distributed worker" tier described in the spec: N independent
 * polling threads each do a blocking right-pop (BRPOP semantics) against the
 * shared Redis list, so multiple workflow runs execute concurrently and -
 * if this service were horizontally scaled to multiple instances - every
 * instance would compete fairly for the same queue with no extra
 * coordination needed (Redis guarantees each list element is popped by
 * exactly one consumer).
 */
@Slf4j
@Component
public class ExecutionQueueWorker {

    private final StringRedisTemplate redisTemplate;
    private final ExecutionService executionService;
    private final ObjectMapper objectMapper = new ObjectMapper();

    @Value("${flowboardx.execution.queue-key}")
    private String queueKey;

    @Value("${flowboardx.execution.worker-pool-size:4}")
    private int workerPoolSize;

    private ExecutorService pollers;
    private volatile boolean running = true;

    public ExecutionQueueWorker(StringRedisTemplate redisTemplate, ExecutionService executionService) {
        this.redisTemplate = redisTemplate;
        this.executionService = executionService;
    }

    @PostConstruct
    public void start() {
        pollers = Executors.newFixedThreadPool(workerPoolSize);
        for (int i = 0; i < workerPoolSize; i++) {
            int workerId = i;
            pollers.submit(() -> pollLoop(workerId));
        }
        log.info("ExecutionQueueWorker started with {} polling threads on queue '{}'", workerPoolSize, queueKey);
    }

    private void pollLoop(int workerId) {
        while (running) {
            try {
                String payload = redisTemplate.opsForList().rightPop(queueKey, Duration.ofSeconds(5));
                if (payload == null) continue; // timed out, poll again
                ExecutionMessage message = objectMapper.readValue(payload, ExecutionMessage.class);
                log.info("Worker-{} picked up run {}", workerId, message.getWorkflowRunId());
                executionService.processQueuedRun(message);
            } catch (Exception e) {
                log.error("Worker-{} failed to process queue message", workerId, e);
            }
        }
    }

    @PreDestroy
    public void stop() {
        running = false;
        if (pollers != null) pollers.shutdownNow();
    }
}
