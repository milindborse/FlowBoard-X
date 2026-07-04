package com.flowboardx.engine.executor;

import com.flowboardx.domain.entity.WorkflowNode;
import com.flowboardx.domain.enums.NodeType;
import com.flowboardx.engine.ExecutionContext;
import com.flowboardx.engine.NodeExecutionResult;
import org.springframework.stereotype.Component;

import java.util.Map;

/**
 * A configuration node: { "maxAttempts": 5, "baseBackoffMs": 1000 }.
 * Registers an override that the DagEngine applies to this node's direct
 * downstream nodes, on top of any retry settings already present on the
 * WorkflowNode entity itself. Lets a branch say "wrap everything after me
 * in this retry policy" without editing every individual node.
 */
@Component
public class RetryExecutor extends AbstractNodeExecutor {
    @Override
    public NodeType supports() { return NodeType.RETRY; }

    @Override
    public NodeExecutionResult execute(WorkflowNode node, Map<String, Object> input, ExecutionContext context) {
        Map<String, Object> config = parseConfig(node);
        int maxAttempts = ((Number) config.getOrDefault("maxAttempts", 3)).intValue();
        long baseBackoffMs = ((Number) config.getOrDefault("baseBackoffMs", 1000)).longValue();
        context.getRetryOverrides().put(node.getClientNodeId(),
                new ExecutionContext.RetryOverride(maxAttempts, baseBackoffMs));
        return NodeExecutionResult.success(input,
                "Retry policy registered for downstream nodes: maxAttempts=" + maxAttempts + ", baseBackoffMs=" + baseBackoffMs);
    }
}
