package com.flowboardx.engine.executor;

import com.flowboardx.domain.entity.WorkflowNode;
import com.flowboardx.domain.enums.NodeType;
import com.flowboardx.engine.ExecutionContext;
import com.flowboardx.engine.NodeExecutionResult;
import org.springframework.stereotype.Component;

import java.util.Map;

@Component
public class ManualTriggerExecutor extends AbstractNodeExecutor {
    @Override
    public NodeType supports() { return NodeType.MANUAL_TRIGGER; }

    @Override
    public NodeExecutionResult execute(WorkflowNode node, Map<String, Object> input, ExecutionContext context) {
        return NodeExecutionResult.success(context.getTriggerPayload(), "Manual trigger fired by user action");
    }
}
