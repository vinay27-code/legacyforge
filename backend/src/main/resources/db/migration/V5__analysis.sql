-- V5: Analysis results for ingested repos.

CREATE TABLE analysis_reports (
    id                UUID PRIMARY KEY,
    repo_id           UUID NOT NULL REFERENCES repos(id) ON DELETE CASCADE,
    status            VARCHAR(32) NOT NULL,
    frameworks        JSONB       NOT NULL,
    summary           JSONB       NOT NULL,
    findings          JSONB       NOT NULL,
    total_java_files  INTEGER     NOT NULL DEFAULT 0,
    total_java_loc    BIGINT      NOT NULL DEFAULT 0,
    max_complexity    INTEGER     NOT NULL DEFAULT 0,
    avg_complexity    NUMERIC(8,2) NOT NULL DEFAULT 0,
    error_message     TEXT,
    created_at        TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    updated_at        TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    CONSTRAINT analysis_status_check CHECK (status IN ('PENDING', 'READY', 'FAILED'))
);

CREATE INDEX idx_analysis_repo_id ON analysis_reports(repo_id);
-- Only one active analysis per repo (latest); older ones would be deleted first if we rerun.
CREATE UNIQUE INDEX idx_analysis_repo_unique ON analysis_reports(repo_id);

CREATE TABLE file_analyses (
    id            UUID PRIMARY KEY,
    analysis_id   UUID NOT NULL REFERENCES analysis_reports(id) ON DELETE CASCADE,
    file_id       UUID NOT NULL REFERENCES repo_files(id) ON DELETE CASCADE,
    file_path     VARCHAR(1024) NOT NULL,
    class_name    VARCHAR(512),
    package_name  VARCHAR(512),
    loc           INTEGER NOT NULL DEFAULT 0,
    method_count  INTEGER NOT NULL DEFAULT 0,
    complexity    INTEGER NOT NULL DEFAULT 0,
    imports       JSONB   NOT NULL,
    frameworks    JSONB   NOT NULL,
    findings      JSONB   NOT NULL,
    created_at    TIMESTAMPTZ NOT NULL DEFAULT NOW()
);

CREATE INDEX idx_file_analyses_analysis_id ON file_analyses(analysis_id);
CREATE INDEX idx_file_analyses_complexity ON file_analyses(complexity DESC);
