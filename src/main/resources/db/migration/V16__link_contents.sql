SET LOCAL lock_timeout = '5s';
SET LOCAL statement_timeout = '60s';

CREATE TABLE link_contents (
    id BIGSERIAL PRIMARY KEY,
    link_id BIGINT NOT NULL REFERENCES links(id) ON DELETE CASCADE,
    source_url TEXT NOT NULL,
    status VARCHAR(20) NOT NULL,
    priority SMALLINT NOT NULL DEFAULT 0,
    generation INT NOT NULL DEFAULT 0,
    claim_token UUID,
    claim_count SMALLINT NOT NULL DEFAULT 0,
    attempt SMALLINT NOT NULL DEFAULT 0,
    started_at TIMESTAMP,
    next_attempt_at TIMESTAMP NOT NULL DEFAULT now(),
    failure_code VARCHAR(40),
    canonical_url TEXT,
    final_url TEXT,
    title VARCHAR(500),
    site_name VARCHAR(200),
    author VARCHAR(200),
    published_at TIMESTAMP,
    language VARCHAR(16),
    content_text TEXT,
    content_length INT,
    content_hash CHAR(64),
    is_truncated BOOLEAN NOT NULL DEFAULT FALSE,
    excerpt VARCHAR(101),
    collected_at TIMESTAMP,
    created_at TIMESTAMP NOT NULL DEFAULT now(),
    updated_at TIMESTAMP NOT NULL DEFAULT now(),
    CONSTRAINT link_contents_link_id_unique UNIQUE (link_id),
    CONSTRAINT link_contents_status_check CHECK (status IN ('PENDING', 'COLLECTED', 'FAILED', 'BLOCKED')),
    CONSTRAINT link_contents_text_length_check CHECK (content_text IS NULL OR char_length(content_text) <= 100000),
    CONSTRAINT link_contents_excerpt_check CHECK (excerpt IS NULL OR (char_length(excerpt) BETWEEN 2 AND 101 AND right(excerpt, 1) = '…')),
    CONSTRAINT link_contents_state_check CHECK (
        (
            status = 'COLLECTED'
            AND content_text IS NOT NULL
            AND content_length IS NOT NULL
            AND content_hash IS NOT NULL
            AND collected_at IS NOT NULL
        )
        OR
        (
            status <> 'COLLECTED'
            AND content_text IS NULL
            AND content_length IS NULL
            AND content_hash IS NULL
            AND excerpt IS NULL
            AND collected_at IS NULL
            AND canonical_url IS NULL
            AND final_url IS NULL
            AND title IS NULL
            AND site_name IS NULL
            AND author IS NULL
            AND published_at IS NULL
            AND language IS NULL
            AND is_truncated = FALSE
        )
    )
);

CREATE INDEX link_contents_due_idx ON link_contents (priority, next_attempt_at, id) WHERE status = 'PENDING';

CREATE INDEX link_contents_content_text_bigm_idx ON link_contents USING GIN (lower(content_text) public.gin_bigm_ops);
