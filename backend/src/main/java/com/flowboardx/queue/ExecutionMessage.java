package com.flowboardx.queue;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.util.List;
import java.util.Set;
import java.util.UUID;

/**
 * The payload pushed onto the Redis execution queue. A single message
 * shape drives all three execution modes - the worker doesn't need to know
 * whether it's processing a fresh run, a replay, or an approval resume,
 * only which nodes are already done and which ones to start from.
 */
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class ExecutionMessage {
    private UUID workflowRunId;
    private List<String> startNodeIds;
    private Set<String> alreadyCompletedNodeIds;
}
