package com.flowboardx.engine;

import com.flowboardx.domain.enums.NodeExecutionStatus;
import lombok.Builder;
import lombok.Getter;

import java.util.Map;

@Getter
@Builder
public class NodeExecutionResult {
    private NodeExecutionStatus status;
    private Map<String, Object> output;
    private String log;
    private String errorMessage;
    /** Only set by CONDITION nodes: "true" or "false". Used to choose which outgoing edge fires. */
    private String conditionBranch;

    public static NodeExecutionResult success(Map<String, Object> output, String log) {
        return NodeExecutionResult.builder()
                .status(NodeExecutionStatus.SUCCEEDED)
                .output(output)
                .log(log)
                .build();
    }

    public static NodeExecutionResult failure(String errorMessage, String log) {
        return NodeExecutionResult.builder()
                .status(NodeExecutionStatus.FAILED)
                .errorMessage(errorMessage)
                .log(log)
                .build();
    }
}
