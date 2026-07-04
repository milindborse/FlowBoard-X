package com.flowboardx.engine.executor;

import com.flowboardx.domain.entity.WorkflowNode;
import com.flowboardx.domain.enums.NodeExecutionStatus;
import com.flowboardx.domain.enums.NodeType;
import com.flowboardx.engine.ExecutionContext;
import com.flowboardx.engine.NodeExecutionResult;
import org.springframework.stereotype.Component;

import java.util.Map;

/**
 * Always returns AWAITING_APPROVAL on first execution - the DagEngine
 * detects this status, persists run state, and pauses that branch of the
 * graph until a human calls POST /api/runs/{id}/nodes/{nodeId}/approve.
 * Resuming reuses the exact same replay mechanism as failure-replay (see
 * DagEngine#execute resumeFrom support).
 */
@Component
public class ApprovalExecutor extends AbstractNodeExecutor {
    @Override
    public NodeType supports() { return NodeType.APPROVAL; }

    @Override
    public NodeExecutionResult execute(WorkflowNode node, Map<String, Object> input, ExecutionContext context) {
        return NodeExecutionResult.builder()
                .status(NodeExecutionStatus.AWAITING_APPROVAL)
                .output(input)
                .log("Workflow paused - awaiting human approval at node '" + node.getLabel() + "'")
                .build();
    }
}
