-- V8: Per-file generated code artifacts from the migration agents.

CREATE TABLE migration_artifacts (
    id             UUID PRIMARY KEY,
    plan_id        UUID NOT NULL REFERENCES migration_plans(id) ON DELETE CASCADE,
    repo_id        UUID NOT NULL REFERENCES repos(id) ON DELETE CASCADE,
    file_path      VARCHAR(1024) NOT NULL,       -- original legacy path
    target_path    VARCHAR(1024),                 -- target path proposed by the agent
    phase_number   INTEGER NOT NULL,
    phase_title    VARCHAR(255) NOT NULL,
    risk           VARCHAR(16) NOT NULL,          -- LOW / MEDIUM / HIGH
    status         VARCHAR(16) NOT NULL,          -- PENDING / RUNNING / SUCCESS / FAILED
    original_code  TEXT,
    generated_code TEXT,
    error_message  TEXT,
    prompt_tokens  INTEGER,
    output_tokens  INTEGER,
    started_at     TIMESTAMPTZ,
    completed_at   TIMESTAMPTZ,
    created_at     TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    CONSTRAINT migration_artifacts_status_check CHECK (status IN ('PENDING', 'RUNNING', 'SUCCESS', 'FAILED'))
);

CREATE INDEX idx_migration_artifacts_plan   ON migration_artifacts(plan_id);
CREATE INDEX idx_migration_artifacts_repo   ON migration_artifacts(repo_id);
CREATE INDEX idx_migration_artifacts_status ON migration_artifacts(status);
