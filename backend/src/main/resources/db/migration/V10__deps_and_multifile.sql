-- V10: Multi-file splitting + generated code dependency graph.

-- Parent link: when the LLM emits multiple `// TARGET:` blocks in one
-- response, we split them into sibling artifacts. The first (primary) is
-- linked to the plan file; the rest carry parent_artifact_id back to it.
ALTER TABLE migration_artifacts
    ADD COLUMN parent_artifact_id UUID
        REFERENCES migration_artifacts(id) ON DELETE CASCADE,
    ADD COLUMN declared_fqn VARCHAR(512);

CREATE INDEX idx_migration_artifacts_parent   ON migration_artifacts(parent_artifact_id);
CREATE INDEX idx_migration_artifacts_declared ON migration_artifacts(declared_fqn);

-- Class-level dependency graph across generated Java artifacts.
CREATE TABLE migration_dependencies (
    id                 UUID PRIMARY KEY,
    repo_id            UUID NOT NULL REFERENCES repos(id) ON DELETE CASCADE,
    from_artifact_id   UUID NOT NULL REFERENCES migration_artifacts(id) ON DELETE CASCADE,
    to_artifact_id     UUID REFERENCES migration_artifacts(id) ON DELETE SET NULL,
    to_class_name      VARCHAR(512) NOT NULL,        -- fully-qualified type name being referenced
    edge_type          VARCHAR(32) NOT NULL,         -- IMPORT | FIELD | METHOD_PARAM | INSTANTIATION
    resolved           BOOLEAN NOT NULL DEFAULT FALSE,
    created_at         TIMESTAMPTZ NOT NULL DEFAULT NOW()
);

CREATE INDEX idx_deps_repo      ON migration_dependencies(repo_id);
CREATE INDEX idx_deps_from      ON migration_dependencies(from_artifact_id);
CREATE INDEX idx_deps_to        ON migration_dependencies(to_artifact_id);
CREATE INDEX idx_deps_resolved  ON migration_dependencies(resolved);
