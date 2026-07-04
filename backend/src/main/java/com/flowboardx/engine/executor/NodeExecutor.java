package com.flowboardx.engine.executor;

import com.flowboardx.domain.entity.WorkflowNode;
import com.flowboardx.domain.enums.NodeType;
import com.flowboardx.engine.ExecutionContext;
import com.flowboardx.engine.NodeExecutionResult;

import java.util.Map;

public interface NodeExecutor {
    NodeType supports();

    /**
     * @param input the already-merged output of every direct upstream node
     *              (computed by the DagEngine before dispatch - executors
     *              never need to know the graph shape themselves).
     */
    NodeExecutionResult execute(WorkflowNode node, Map<String, Object> input, ExecutionContext context) throws Exception;
}
