package com.flowboardx.engine.executor;

import com.flowboardx.domain.entity.WorkflowNode;
import com.flowboardx.domain.enums.NodeType;
import com.flowboardx.engine.ExecutionContext;
import com.flowboardx.engine.NodeExecutionResult;
import org.springframework.stereotype.Component;

import java.util.Map;

/**
 * Collects the merged outputs of every direct upstream branch into a single
 * object. Used after a Parallel Split to fan results back together before
 * continuing the pipeline.
 */
@Component
public class AggregatorExecutor extends AbstractNodeExecutor {
    @Override
    public NodeType supports() { return NodeType.AGGREGATOR; }

    @Override
    public NodeExecutionResult execute(WorkflowNode node, Map<String, Object> input, ExecutionContext context) {
        return NodeExecutionResult.success(input, "Aggregated " + input.size() + " field(s) from upstream branches");
    }
}
