package com.flowboardx.engine.executor;

import com.flowboardx.domain.entity.WorkflowNode;
import com.flowboardx.domain.enums.NodeType;
import com.flowboardx.engine.ExecutionContext;
import com.flowboardx.engine.NodeExecutionResult;
import org.springframework.stereotype.Component;

import java.util.HashMap;
import java.util.Map;

/**
 * Config shape: { "mapping": { "newKey": "source.path.into.input", ... } }
 * Produces a brand-new output object built purely from upstream fields -
 * the standard "reshape data between two systems" step in any pipeline.
 */
@Component
public class TransformExecutor extends AbstractNodeExecutor {
    @Override
    public NodeType supports() { return NodeType.TRANSFORM; }

    @Override
    @SuppressWarnings("unchecked")
    public NodeExecutionResult execute(WorkflowNode node, Map<String, Object> input, ExecutionContext context) {
        Map<String, Object> config = parseConfig(node);
        Map<String, Object> mapping = (Map<String, Object>) config.getOrDefault("mapping", Map.of());

        Map<String, Object> output = new HashMap<>();
        for (Map.Entry<String, Object> entry : mapping.entrySet()) {
            output.put(entry.getKey(), resolvePath(input, String.valueOf(entry.getValue())));
        }
        if (mapping.isEmpty()) {
            output.putAll(input); // passthrough if no mapping configured
        }
        return NodeExecutionResult.success(output, "Transformed " + input.size() + " input field(s) into " + output.size() + " output field(s)");
    }
}
