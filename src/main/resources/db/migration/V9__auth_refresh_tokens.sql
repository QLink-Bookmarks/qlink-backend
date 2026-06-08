CREATE TABLE refresh_tokens (
    id BIGSERIAL PRIMARY KEY,
    user_id BIGINT NOT NULL REFERENCES users(id) ON DELETE CASCADE,
    family_id VARCHAR(36) NOT NULL,
    token TEXT NOT NULL,
    issued_at TIMESTAMP NOT NULL,
    expired_at TIMESTAMP NOT NULL,
    created_at TIMESTAMP NOT NULL DEFAULT now(),
    updated_at TIMESTAMP NOT NULL DEFAULT now(),
    CONSTRAINT refresh_tokens_token_unique UNIQUE (token)
);

CREATE INDEX refresh_tokens_user_family_idx ON refresh_tokens(user_id, family_id);
