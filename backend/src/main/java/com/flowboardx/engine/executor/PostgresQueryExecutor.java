package com.flowboardx.engine.executor;

import com.flowboardx.domain.entity.WorkflowNode;
import com.flowboardx.domain.enums.NodeType;
import com.flowboardx.engine.ExecutionContext;
import com.flowboardx.engine.NodeExecutionResult;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Component;

import javax.sql.DataSource;
import java.sql.SQLException;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import org.springframework.jdbc.datasource.SimpleDriverDataSource;

/**
 * Config shape: { "jdbcUrl", "username", "password", "query" }.
 * Connects to a user-specified target Postgres instance at run time (NOT
 * the platform's own metadata database) - this is what lets a workflow
 * query an arbitrary business database as an integration step.
 *
 * NOTE: requires the operator to supply real, reachable credentials in the
 * node config. With no target database configured this node will fail
 * cleanly with a descriptive error rather than crash the worker.
 */
@Component
public class PostgresQueryExecutor extends AbstractNodeExecutor {

    @Override
    public NodeType supports() { return NodeType.POSTGRES_QUERY; }

    @Override
    public NodeExecutionResult execute(WorkflowNode node, Map<String, Object> input, ExecutionContext context) {
        Map<String, Object> config = parseConfig(node);
        String jdbcUrl = (String) config.get("jdbcUrl");
        String username = (String) config.getOrDefault("username", "");
        String password = (String) config.getOrDefault("password", "");
        String query = (String) config.get("query");

        if (jdbcUrl == null || query == null) {
            return NodeExecutionResult.failure("Postgres Query node requires 'jdbcUrl' and 'query' in its config", null);
        }

        try {
            DataSource ds = new SimpleDriverDataSource(new org.postgresql.Driver(), jdbcUrl, username, password);
            JdbcTemplate jdbcTemplate = new JdbcTemplate(ds);
            List<Map<String, Object>> rows = jdbcTemplate.queryForList(query);

            Map<String, Object> output = new HashMap<>();
            output.put("rowCount", rows.size());
            output.put("rows", rows);
            return NodeExecutionResult.success(output, "Query returned " + rows.size() + " row(s)");
        } catch (Exception e) {
    return NodeExecutionResult.failure(
        "Query execution failed: " + e.getMessage(),
        null
    );
}
    }
}
