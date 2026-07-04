package com.flowboardx.engine.executor;

import com.flowboardx.domain.entity.WorkflowNode;
import com.flowboardx.domain.enums.NodeType;
import com.flowboardx.engine.ExecutionContext;
import com.flowboardx.engine.NodeExecutionResult;
import org.springframework.http.*;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestTemplate;

import java.util.HashMap;
import java.util.Map;

/**
 * Config shape: { "url": "...", "method": "GET|POST|PUT|PATCH|DELETE",
 *                 "headers": {...}, "bodyField": "path.into.input" }
 */
@Component
public class HttpRequestExecutor extends AbstractNodeExecutor {

    private final RestTemplate restTemplate = new RestTemplate();

    @Override
    public NodeType supports() { return NodeType.HTTP_REQUEST; }

    @Override
    @SuppressWarnings("unchecked")
    public NodeExecutionResult execute(WorkflowNode node, Map<String, Object> input, ExecutionContext context) {
        System.out.println("========== NODE ==========");
        System.out.println("Node ID      : " + node.getClientNodeId());
        System.out.println("Node Label   : " + node.getLabel());
        System.out.println("Raw Config   : " + node.getConfigJson());
        System.out.println("==========================");
        Map<String, Object> config = parseConfig(node);
        System.out.println(config);
        String url = (String) config.get("url");
        if (url == null || url.isBlank()) {
            return NodeExecutionResult.failure("HTTP Request node is missing a 'url' in its config", null);
        }
        HttpMethod method = HttpMethod.valueOf(((String) config.getOrDefault("method", "GET")).toUpperCase());

        HttpHeaders headers = new HttpHeaders();
        Map<String, Object> configHeaders = (Map<String, Object>) config.getOrDefault("headers", Map.of());
        configHeaders.forEach((k, v) -> headers.add(k, String.valueOf(v)));
        headers.setContentType(MediaType.APPLICATION_JSON);

        Object body = config.containsKey("bodyField") ? resolvePath(input, (String) config.get("bodyField")) : input;
        HttpEntity<Object> entity = new HttpEntity<>(body, headers);

        try {
            ResponseEntity<String> response = restTemplate.exchange(url, method, entity, String.class);
            Map<String, Object> output = new HashMap<>();
            output.put("statusCode", response.getStatusCode().value());
            output.put("body", response.getBody());
            return NodeExecutionResult.success(output, "HTTP " + method + " " + url + " -> " + response.getStatusCode());
        } catch (Exception e) {
            return NodeExecutionResult.failure("HTTP request failed: " + e.getMessage(), "HTTP " + method + " " + url + " failed");
        }
    }
}
