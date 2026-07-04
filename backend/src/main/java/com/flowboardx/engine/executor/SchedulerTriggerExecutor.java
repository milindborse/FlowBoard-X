package com.flowboardx.engine.executor;

import com.flowboardx.domain.entity.WorkflowNode;
import com.flowboardx.domain.enums.NodeType;
import com.flowboardx.engine.ExecutionContext;
import com.flowboardx.engine.NodeExecutionResult;
import org.springframework.stereotype.Component;

import java.time.Instant;
import java.util.HashMap;
import java.util.Map;

@Component
public class SchedulerTriggerExecutor extends AbstractNodeExecutor {
    @Override
    public NodeType supports() { return NodeType.SCHEDULER_TRIGGER; }

    @Override
    public NodeExecutionResult execute(WorkflowNode node, Map<String, Object> input, ExecutionContext context) {
        Map<String, Object> output = new HashMap<>(context.getTriggerPayload());
        output.put("triggeredAt", Instant.now().toString());
        return NodeExecutionResult.success(output, "Scheduled run started by cron trigger");
    }
}
