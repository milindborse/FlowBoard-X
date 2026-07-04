package com.flowboardx.engine.executor;

import com.flowboardx.domain.entity.WorkflowNode;
import com.flowboardx.domain.enums.NodeType;
import com.flowboardx.engine.ExecutionContext;
import com.flowboardx.engine.NodeExecutionResult;
import org.springframework.stereotype.Component;

import java.util.Map;

@Component
public class DelayExecutor extends AbstractNodeExecutor {

    private static final long MAX_DELAY_MS = 5 * 60_000L; // safety cap

    @Override
    public NodeType supports() { return NodeType.DELAY; }

    @Override
    public NodeExecutionResult execute(WorkflowNode node, Map<String, Object> input, ExecutionContext context) throws InterruptedException {
        Map<String, Object> config = parseConfig(node);
        long delayMs = Math.min(((Number) config.getOrDefault("delayMs", 1000)).longValue(), MAX_DELAY_MS);
        Thread.sleep(delayMs);
        return NodeExecutionResult.success(input, "Delayed execution by " + delayMs + "ms");
    }
}
