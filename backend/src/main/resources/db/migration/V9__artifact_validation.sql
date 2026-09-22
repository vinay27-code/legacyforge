-- V9: Validation status + retry tracking for migration artifacts.

ALTER TABLE migration_artifacts
    ADD COLUMN validation_status  VARCHAR(16) NOT NULL DEFAULT 'SKIPPED',
    ADD COLUMN validation_errors  TEXT,
    ADD COLUMN retry_count        INTEGER NOT NULL DEFAULT 0;

ALTER TABLE migration_artifacts
    ADD CONSTRAINT migration_artifacts_validation_check
    CHECK (validation_status IN ('VALID', 'INVALID', 'SKIPPED'));

CREATE INDEX idx_migration_artifacts_validation ON migration_artifacts(validation_status);
