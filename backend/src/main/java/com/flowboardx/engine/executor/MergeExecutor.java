package com.flowboardx.engine.executor;

import com.flowboardx.domain.entity.WorkflowNode;
import com.flowboardx.domain.enums.NodeType;
import com.flowboardx.engine.ExecutionContext;
import com.flowboardx.engine.NodeExecutionResult;
import org.springframework.stereotype.Component;

import java.util.Map;

/**
 * A join point: the DAG engine will not dispatch this node until every
 * incoming branch has resolved (in-degree counter reaches 0), so by the
 * time execute() runs, `input` already contains the merged output of all
 * parallel branches that fed into it.
 */
@Component
public class MergeExecutor extends AbstractNodeExecutor {
    @Override
    public NodeType supports() { return NodeType.MERGE; }

    @Override
    public NodeExecutionResult execute(WorkflowNode node, Map<String, Object> input, ExecutionContext context) {
        return NodeExecutionResult.success(input, "Joined " + input.size() + " field(s) from parallel branches");
    }
}
