package com.flowboardx.engine.executor;

import com.flowboardx.domain.entity.WorkflowNode;
import com.flowboardx.domain.enums.NodeType;
import com.flowboardx.engine.ExecutionContext;
import com.flowboardx.engine.NodeExecutionResult;
import org.springframework.stereotype.Component;

import java.util.Map;

/**
 * Semantically a pass-through: the actual parallelism comes from the DAG
 * engine dispatching every outgoing edge of this node concurrently the
 * instant it succeeds (see DagEngine#onNodeCompleted). This node exists so
 * the visual editor has an explicit "fan-out starts here" marker.
 */
@Component
public class ParallelSplitExecutor extends AbstractNodeExecutor {
    @Override
    public NodeType supports() { return NodeType.PARALLEL_SPLIT; }

    @Override
    public NodeExecutionResult execute(WorkflowNode node, Map<String, Object> input, ExecutionContext context) {
        return NodeExecutionResult.success(input, "Fan-out point reached - downstream branches will execute concurrently");
    }
}
