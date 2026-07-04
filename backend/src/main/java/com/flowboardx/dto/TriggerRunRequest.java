package com.flowboardx.dto;

import com.flowboardx.domain.enums.TriggerType;
import lombok.Getter;
import lombok.Setter;

import java.util.Map;
import java.util.UUID;

@Getter @Setter
public class TriggerRunRequest {
    private TriggerType triggerType = TriggerType.MANUAL;
    private Map<String, Object> inputPayload;
    private UUID replayFromRunId;
    private String replayFromNodeId;
}
