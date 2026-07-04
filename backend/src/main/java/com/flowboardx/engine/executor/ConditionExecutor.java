package com.flowboardx.engine.executor;

import com.flowboardx.domain.entity.WorkflowNode;
import com.flowboardx.domain.enums.NodeExecutionStatus;
import com.flowboardx.domain.enums.NodeType;
import com.flowboardx.engine.ExecutionContext;
import com.flowboardx.engine.NodeExecutionResult;
import org.springframework.stereotype.Component;

import java.math.BigDecimal;
import java.util.Map;

/**
 * Evaluates a single comparison against upstream data: { field, operator, value }.
 * Deliberately NOT a general-purpose script/expression engine (no eval of
 * arbitrary user code) - keeps workflow definitions safe to store and run
 * multi-tenant. Determines which downstream branch ("true"/"false") fires.
 */
@Component
public class ConditionExecutor extends AbstractNodeExecutor {
    @Override
    public NodeType supports() { return NodeType.CONDITION; }

    @Override
    public NodeExecutionResult execute(WorkflowNode node, Map<String, Object> input, ExecutionContext context) {
        Map<String, Object> config = parseConfig(node);

        String field = (String) config.getOrDefault("field", "");
        String operator = (String) config.getOrDefault("operator", "==");
        Object expected = config.get("value");

        Object actual = resolvePath(input, field);
        boolean result = evaluate(actual, operator, expected);

        return NodeExecutionResult.builder()
                .status(NodeExecutionStatus.SUCCEEDED)
                .output(Map.of("field", field, "actual", String.valueOf(actual), "result", result))
                .log("Condition '" + field + " " + operator + " " + expected + "' evaluated to " + result)
                .conditionBranch(result ? "true" : "false")
                .build();
    }

    private boolean evaluate(Object actual, String operator, Object expected) {
        if (actual == null) return "==".equals(operator) && expected == null;
        try {
            if (actual instanceof Number || (expected != null && isNumeric(expected.toString()))) {
                BigDecimal a = new BigDecimal(actual.toString());
                BigDecimal b = new BigDecimal(expected.toString());
                int cmp = a.compareTo(b);
                return switch (operator) {
                    case ">" -> cmp > 0;
                    case ">=" -> cmp >= 0;
                    case "<" -> cmp < 0;
                    case "<=" -> cmp <= 0;
                    case "==" -> cmp == 0;
                    case "!=" -> cmp != 0;
                    default -> false;
                };
            }
        } catch (NumberFormatException ignored) {
            // fall through to string comparison
        }
        String a = actual.toString();
        String b = String.valueOf(expected);
        return switch (operator) {
            case "==" -> a.equals(b);
            case "!=" -> !a.equals(b);
            case "contains" -> a.contains(b);
            default -> false;
        };
    }

    private boolean isNumeric(String s) {
        try { new BigDecimal(s); return true; } catch (Exception e) { return false; }
    }
}
