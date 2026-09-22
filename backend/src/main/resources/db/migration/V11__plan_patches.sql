-- V11: Plan-patch audit trail + drop the parent FK that fights parallel inserts.

-- The parent link on migration_artifacts is a soft reference — parents and
-- children always live and die together via deleteByRepoId. The FK was
-- triggering constraint violations when parallel worker threads hit
-- transaction isolation edge cases inserting children before the parent
-- transaction was visible to their connection. Drop it; keep the column.
ALTER TABLE migration_artifacts
    DROP CONSTRAINT IF EXISTS migration_artifacts_parent_artifact_id_fkey;

-- Audit trail: every time we patch a plan with the broken-links feedback
-- loop we record what got added and why.
CREATE TABLE plan_patches (
    id                  UUID PRIMARY KEY,
    plan_id             UUID NOT NULL REFERENCES migration_plans(id) ON DELETE CASCADE,
    repo_id             UUID NOT NULL REFERENCES repos(id) ON DELETE CASCADE,
    broken_class_names  TEXT NOT NULL,          -- comma-separated FQNs used as input
    files_added         INTEGER NOT NULL,
    phases_added        INTEGER NOT NULL,
    provider            VARCHAR(128) NOT NULL,
    prompt_tokens       INTEGER,
    output_tokens       INTEGER,
    created_at          TIMESTAMPTZ NOT NULL DEFAULT NOW()
);

CREATE INDEX idx_plan_patches_plan ON plan_patches(plan_id);
CREATE INDEX idx_plan_patches_repo ON plan_patches(repo_id);
