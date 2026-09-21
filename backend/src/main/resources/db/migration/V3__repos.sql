-- V3: Ingested legacy repositories and their files.

CREATE TABLE repos (
    id               UUID PRIMARY KEY,
    user_id          UUID NOT NULL REFERENCES users(id) ON DELETE CASCADE,
    name             VARCHAR(255) NOT NULL,
    source_type      VARCHAR(32)  NOT NULL,
    source_url       VARCHAR(1024),
    file_count       INTEGER      NOT NULL DEFAULT 0,
    total_size_bytes BIGINT       NOT NULL DEFAULT 0,
    status           VARCHAR(32)  NOT NULL DEFAULT 'READY',
    error_message    TEXT,
    created_at       TIMESTAMPTZ  NOT NULL DEFAULT NOW(),
    updated_at       TIMESTAMPTZ  NOT NULL DEFAULT NOW(),
    CONSTRAINT repos_source_check CHECK (source_type IN ('ZIP', 'GITHUB')),
    CONSTRAINT repos_status_check CHECK (status IN ('PENDING', 'READY', 'FAILED'))
);

CREATE INDEX idx_repos_user_id     ON repos(user_id);
CREATE INDEX idx_repos_created_at  ON repos(created_at DESC);

CREATE TABLE repo_files (
    id          UUID PRIMARY KEY,
    repo_id     UUID NOT NULL REFERENCES repos(id) ON DELETE CASCADE,
    path        VARCHAR(1024) NOT NULL,
    size_bytes  BIGINT       NOT NULL,
    language    VARCHAR(64),
    sha256      CHAR(64)     NOT NULL,
    is_binary   BOOLEAN      NOT NULL DEFAULT FALSE,
    content     TEXT,
    created_at  TIMESTAMPTZ  NOT NULL DEFAULT NOW(),
    UNIQUE (repo_id, path)
);

CREATE INDEX idx_repo_files_repo_id  ON repo_files(repo_id);
CREATE INDEX idx_repo_files_language ON repo_files(language);
