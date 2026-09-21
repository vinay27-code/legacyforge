-- V4: Align sha256 type with JPA's default (VARCHAR).
ALTER TABLE repo_files ALTER COLUMN sha256 TYPE VARCHAR(64);
