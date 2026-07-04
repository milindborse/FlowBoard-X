package com.flowboardx.engine.executor;

import com.flowboardx.domain.entity.WorkflowNode;
import com.flowboardx.domain.enums.NodeType;
import com.flowboardx.engine.ExecutionContext;
import com.flowboardx.engine.NodeExecutionResult;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Component;

import java.util.Map;

/** Config shape: { "channel": "...", "messageField": "path.into.input" } */
@Component
public class RedisPublishExecutor extends AbstractNodeExecutor {

    private final StringRedisTemplate redisTemplate;

    public RedisPublishExecutor(StringRedisTemplate redisTemplate) {
        this.redisTemplate = redisTemplate;
    }

    @Override
    public NodeType supports() { return NodeType.REDIS_PUBLISH; }

    @Override
    public NodeExecutionResult execute(WorkflowNode node, Map<String, Object> input, ExecutionContext context) {
        Map<String, Object> config = parseConfig(node);
        String channel = (String) config.get("channel");
        if (channel == null || channel.isBlank()) {
            return NodeExecutionResult.failure("Redis Publish node requires a 'channel' in its config", null);
        }
        Object message = config.containsKey("messageField")
                ? resolvePath(input, (String) config.get("messageField"))
                : input;
        try {
            redisTemplate.convertAndSend(channel, MAPPER.writeValueAsString(message));
            return NodeExecutionResult.success(Map.of("channel", channel, "published", true),
                    "Published message to Redis channel '" + channel + "'");
        } catch (Exception e) {
            return NodeExecutionResult.failure("Redis publish failed: " + e.getMessage(), null);
        }
    }
}
