package com.flowboardx.controller;

import com.flowboardx.domain.enums.TriggerType;
import com.flowboardx.dto.TriggerRunRequest;
import com.flowboardx.dto.RunResponse;
import com.flowboardx.service.ExecutionService;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.Map;
import java.util.UUID;

@RestController
@RequestMapping("/webhooks")
@RequiredArgsConstructor
public class WebhookController {

    private final ExecutionService executionService;

    @PostMapping("/{workflowId}")
    public ResponseEntity<RunResponse> receive(
            @PathVariable UUID workflowId,
            @RequestBody(required = false) Map<String, Object> payload) {
        TriggerRunRequest request = new TriggerRunRequest();
        request.setTriggerType(TriggerType.WEBHOOK);
        request.setInputPayload(payload);
        return ResponseEntity.ok(executionService.triggerRun(workflowId, request, null));
    }
}
