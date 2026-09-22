-- V6: Code chunks with embeddings for semantic search / RAG.

CREATE EXTENSION IF NOT EXISTS vector;


CREATE TABLE code_chunks (
    id            UUID PRIMARY KEY,
    repo_id       UUID NOT NULL REFERENCES repos(id) ON DELETE CASCADE,
    file_id       UUID NOT NULL REFERENCES repo_files(id) ON DELETE CASCADE,
    file_path     VARCHAR(1024) NOT NULL,
    chunk_type    VARCHAR(32) NOT NULL,
    chunk_index   INTEGER NOT NULL,
    class_name    VARCHAR(512),
    method_name   VARCHAR(255),
    start_line    INTEGER,
    end_line      INTEGER,
    content       TEXT NOT NULL,
    embedding     vector(768) NOT NULL,
    created_at    TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    CONSTRAINT code_chunks_type_check CHECK (chunk_type IN ('METHOD', 'CLASS', 'WHOLE_FILE', 'BLOCK'))
);

CREATE INDEX idx_code_chunks_repo_id  ON code_chunks(repo_id);
CREATE INDEX idx_code_chunks_file_id  ON code_chunks(file_id);
-- ivfflat needs `analyze` to work well; the cost of building it up-front is negligible for our sizes.
CREATE INDEX idx_code_chunks_embed    ON code_chunks USING ivfflat (embedding vector_cosine_ops) WITH (lists = 100);
