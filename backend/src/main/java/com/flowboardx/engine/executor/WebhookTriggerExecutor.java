package com.flowboardx.engine.executor;

import com.flowboardx.domain.entity.WorkflowNode;
import com.flowboardx.domain.enums.NodeType;
import com.flowboardx.engine.ExecutionContext;
import com.flowboardx.engine.NodeExecutionResult;
import org.springframework.stereotype.Component;

import java.util.Map;

@Component
public class WebhookTriggerExecutor extends AbstractNodeExecutor {
    @Override
    public NodeType supports() { return NodeType.WEBHOOK_TRIGGER; }

    @Override
    public NodeExecutionResult execute(WorkflowNode node, Map<String, Object> input, ExecutionContext context) {
        return NodeExecutionResult.success(context.getTriggerPayload(), "Webhook payload received and validated");
    }
}
