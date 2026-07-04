package com.flowboardx.engine.executor;

import com.flowboardx.domain.entity.WorkflowNode;
import com.flowboardx.domain.enums.NodeType;
import com.flowboardx.engine.ExecutionContext;
import com.flowboardx.engine.NodeExecutionResult;
import org.springframework.mail.SimpleMailMessage;
import org.springframework.mail.javamail.JavaMailSender;
import org.springframework.stereotype.Component;

import java.util.Map;

/**
 * Config shape: { "to", "subject", "bodyField" }. Requires SMTP credentials
 * (spring.mail.* / SMTP_USER / SMTP_PASSWORD env vars) to actually deliver -
 * see README "Manual Steps" for setup. Without credentials configured this
 * fails cleanly per-node rather than crashing the worker.
 */
@Component
public class EmailExecutor extends AbstractNodeExecutor {

    private final JavaMailSender mailSender;

    public EmailExecutor(JavaMailSender mailSender) {
        this.mailSender = mailSender;
    }

    @Override
    public NodeType supports() { return NodeType.EMAIL; }

    @Override
    public NodeExecutionResult execute(WorkflowNode node, Map<String, Object> input, ExecutionContext context) {
        Map<String, Object> config = parseConfig(node);
        String to = (String) config.get("to");
        String subject = (String) config.getOrDefault("subject", "FlowBoard X Notification");
        Object body = config.containsKey("bodyField") ? resolvePath(input, (String) config.get("bodyField")) : input;

        if (to == null || to.isBlank()) {
            return NodeExecutionResult.failure("Email node requires a 'to' address in its config", null);
        }
        try {
            SimpleMailMessage message = new SimpleMailMessage();
            message.setTo(to);
            message.setSubject(subject);
            message.setText(String.valueOf(body));
            mailSender.send(message);
            return NodeExecutionResult.success(Map.of("to", to, "sent", true), "Email sent to " + to);
        } catch (Exception e) {
            return NodeExecutionResult.failure("Email delivery failed: " + e.getMessage(), null);
        }
    }
}
