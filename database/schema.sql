-- FlowBoard X - Full Database Schema
-- Run this manually OR let spring.jpa.hibernate.ddl-auto=update create it automatically.
-- Use this file as the authoritative reference and for manual Postgres setup.

CREATE EXTENSION IF NOT EXISTS "pgcrypto";

-- ─── Users ───────────────────────────────────────────────────────────────────
CREATE TABLE IF NOT EXISTS users (
    id            UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    email         VARCHAR(255) NOT NULL UNIQUE,
    password_hash VARCHAR(255) NOT NULL,
    display_name  VARCHAR(255) NOT NULL,
    role          VARCHAR(50)  NOT NULL DEFAULT 'EDITOR',
    active        BOOLEAN      NOT NULL DEFAULT TRUE,
    created_at    TIMESTAMPTZ  NOT NULL DEFAULT NOW(),
    updated_at    TIMESTAMPTZ  NOT NULL DEFAULT NOW()
);

-- ─── Workflows ───────────────────────────────────────────────────────────────
CREATE TABLE IF NOT EXISTS workflows (
    id                      UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    name                    VARCHAR(255) NOT NULL,
    description             VARCHAR(1000),
    owner_id                UUID REFERENCES users(id) ON DELETE SET NULL,
    active                  BOOLEAN     NOT NULL DEFAULT TRUE,
    is_template             BOOLEAN     NOT NULL DEFAULT FALSE,
    current_version_number  INTEGER     NOT NULL DEFAULT 1,
    cron_expression         VARCHAR(100),
    created_at              TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    updated_at              TIMESTAMPTZ NOT NULL DEFAULT NOW()
);

CREATE INDEX IF NOT EXISTS idx_workflows_owner ON workflows(owner_id);
CREATE INDEX IF NOT EXISTS idx_workflows_active ON workflows(active);

-- ─── Workflow Versions ───────────────────────────────────────────────────────
CREATE TABLE IF NOT EXISTS workflow_versions (
    id                   UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    workflow_id          UUID        NOT NULL REFERENCES workflows(id) ON DELETE CASCADE,
    version_number       INTEGER     NOT NULL,
    published            BOOLEAN     NOT NULL DEFAULT FALSE,
    graph_snapshot_json  JSONB,
    change_summary       VARCHAR(500),
    created_at           TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    UNIQUE (workflow_id, version_number)
);

CREATE INDEX IF NOT EXISTS idx_wv_workflow ON workflow_versions(workflow_id);

-- ─── Workflow Nodes ──────────────────────────────────────────────────────────
CREATE TABLE IF NOT EXISTS workflow_nodes (
    id                    UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    workflow_version_id   UUID         NOT NULL REFERENCES workflow_versions(id) ON DELETE CASCADE,
    client_node_id        VARCHAR(255) NOT NULL,
    label                 VARCHAR(255) NOT NULL,
    type                  VARCHAR(100) NOT NULL,
    position_x            DOUBLE PRECISION NOT NULL DEFAULT 0,
    position_y            DOUBLE PRECISION NOT NULL DEFAULT 0,
    config_json           JSONB,
    retry_max_attempts    INTEGER,
    retry_base_backoff_ms BIGINT
);

CREATE INDEX IF NOT EXISTS idx_wn_version ON workflow_nodes(workflow_version_id);

-- ─── Workflow Edges ──────────────────────────────────────────────────────────
CREATE TABLE IF NOT EXISTS workflow_edges (
    id                     UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    workflow_version_id    UUID         NOT NULL REFERENCES workflow_versions(id) ON DELETE CASCADE,
    source_client_node_id  VARCHAR(255) NOT NULL,
    target_client_node_id  VARCHAR(255) NOT NULL,
    branch_label           VARCHAR(50)
);

CREATE INDEX IF NOT EXISTS idx_we_version ON workflow_edges(workflow_version_id);

-- ─── Workflow Runs ───────────────────────────────────────────────────────────
CREATE TABLE IF NOT EXISTS workflow_runs (
    id                    UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    workflow_id           UUID        NOT NULL REFERENCES workflows(id),
    workflow_version_id   UUID        NOT NULL REFERENCES workflow_versions(id),
    status                VARCHAR(50) NOT NULL DEFAULT 'PENDING',
    trigger_type          VARCHAR(50) NOT NULL DEFAULT 'MANUAL',
    replayed_from_run_id  UUID,
    input_payload_json    JSONB,
    output_payload_json   JSONB,
    error_message         TEXT,
    started_at            TIMESTAMPTZ,
    finished_at           TIMESTAMPTZ,
    duration_ms           BIGINT,
    created_at            TIMESTAMPTZ NOT NULL DEFAULT NOW()
);

CREATE INDEX IF NOT EXISTS idx_runs_workflow ON workflow_runs(workflow_id);
CREATE INDEX IF NOT EXISTS idx_runs_status   ON workflow_runs(status);
CREATE INDEX IF NOT EXISTS idx_runs_created  ON workflow_runs(created_at DESC);

-- ─── Node Executions ─────────────────────────────────────────────────────────
CREATE TABLE IF NOT EXISTS node_executions (
    id                UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    workflow_run_id   UUID         NOT NULL REFERENCES workflow_runs(id) ON DELETE CASCADE,
    client_node_id    VARCHAR(255) NOT NULL,
    node_label        VARCHAR(255) NOT NULL,
    status            VARCHAR(50)  NOT NULL DEFAULT 'PENDING',
    attempt_number    INTEGER      NOT NULL DEFAULT 1,
    input_json        JSONB,
    output_json       JSONB,
    log_output        TEXT,
    error_message     VARCHAR(4000),
    started_at        TIMESTAMPTZ,
    finished_at       TIMESTAMPTZ,
    duration_ms       BIGINT
);

CREATE INDEX IF NOT EXISTS idx_ne_run ON node_executions(workflow_run_id);

-- ─── Audit Logs ──────────────────────────────────────────────────────────────
CREATE TABLE IF NOT EXISTS audit_logs (
    id               UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    actor_id         UUID REFERENCES users(id) ON DELETE SET NULL,
    workflow_id      UUID REFERENCES workflows(id) ON DELETE SET NULL,
    workflow_run_id  UUID,
    action           VARCHAR(100) NOT NULL,
    details          VARCHAR(2000),
    created_at       TIMESTAMPTZ NOT NULL DEFAULT NOW()
);

CREATE INDEX IF NOT EXISTS idx_audit_workflow ON audit_logs(workflow_id);
CREATE INDEX IF NOT EXISTS idx_audit_created  ON audit_logs(created_at DESC);
