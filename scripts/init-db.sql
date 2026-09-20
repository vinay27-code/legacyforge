-- Runs once when the Postgres container is first created.
-- Enables the pgvector extension so migrations can use vector(N) columns.
CREATE EXTENSION IF NOT EXISTS vector;
