package com.flowboardx.engine.executor;

import com.flowboardx.domain.enums.NodeType;
import org.springframework.stereotype.Component;

import java.util.EnumMap;
import java.util.List;
import java.util.Map;

@Component
public class NodeExecutorRegistry {

    private final Map<NodeType, NodeExecutor> executors = new EnumMap<>(NodeType.class);

    public NodeExecutorRegistry(List<NodeExecutor> allExecutors) {
        for (NodeExecutor executor : allExecutors) {
            executors.put(executor.supports(), executor);
        }
    }

    public NodeExecutor get(NodeType type) {
        NodeExecutor executor = executors.get(type);
        if (executor == null) {
            throw new IllegalStateException("No NodeExecutor registered for type " + type);
        }
        return executor;
    }
}
