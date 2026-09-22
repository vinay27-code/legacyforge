-- V7: LLM-generated phased migration plans, one per repo (latest wins).

CREATE TABLE migration_plans (
    id            UUID PRIMARY KEY,
    repo_id       UUID NOT NULL REFERENCES repos(id) ON DELETE CASCADE,
    provider      VARCHAR(128) NOT NULL,           -- e.g. gemini-2.5-flash @ https://...
    plan_json     TEXT NOT NULL,                    -- structured plan the LLM returned
    prompt_tokens INTEGER,
    output_tokens INTEGER,
    generated_at  TIMESTAMPTZ NOT NULL DEFAULT NOW()
);

CREATE UNIQUE INDEX idx_migration_plans_repo ON migration_plans(repo_id);
