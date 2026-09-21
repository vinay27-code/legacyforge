-- V2: Add a unique constraint on refresh_tokens.token_hash so a rotated or
-- somehow-collided token can't accidentally match another user's row.

CREATE UNIQUE INDEX idx_refresh_tokens_token_hash_unique
    ON refresh_tokens(token_hash);
