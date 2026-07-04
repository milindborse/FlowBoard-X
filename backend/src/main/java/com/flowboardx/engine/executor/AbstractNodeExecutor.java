package com.flowboardx.engine.executor;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.flowboardx.domain.entity.WorkflowNode;

import java.util.HashMap;
import java.util.Map;

public abstract class AbstractNodeExecutor implements NodeExecutor {

    protected static final ObjectMapper MAPPER = new ObjectMapper();

    protected Map<String, Object> parseConfig(WorkflowNode node) {
        try {
            if (node.getConfigJson() == null || node.getConfigJson().isBlank()) {
                return new HashMap<>();
            }
            return MAPPER.readValue(node.getConfigJson(), new TypeReference<Map<String, Object>>() {});
        } catch (Exception e) {
            throw new IllegalArgumentException("Invalid configJson on node " + node.getClientNodeId() + ": " + e.getMessage(), e);
        }
    }

    @SuppressWarnings("unchecked")
    protected Object resolvePath(Map<String, Object> data, String path) {
        if (path == null || path.isBlank()) return null;
        Object current = data;
        for (String part : path.split("\\.")) {
            if (!(current instanceof Map)) return null;
            current = ((Map<String, Object>) current).get(part);
        }
        return current;
    }
}
