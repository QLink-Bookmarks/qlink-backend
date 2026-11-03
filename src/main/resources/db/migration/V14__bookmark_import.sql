CREATE TABLE bookmark_imports (
    id BIGSERIAL PRIMARY KEY,
    owner_id BIGINT NOT NULL REFERENCES users(id) ON DELETE CASCADE,
    client_request_id UUID NOT NULL,
    file_name VARCHAR(255) NOT NULL,
    status VARCHAR(20) NOT NULL,
    link_count INT NOT NULL,
    folder_count INT NOT NULL,
    skipped_count INT NOT NULL,
    failed_count INT NOT NULL,
    reversed_at TIMESTAMP,
    created_at TIMESTAMP NOT NULL DEFAULT now(),
    updated_at TIMESTAMP NOT NULL DEFAULT now(),
    CONSTRAINT bookmark_imports_owner_client_request_unique UNIQUE (owner_id, client_request_id),
    CONSTRAINT bookmark_imports_status_check CHECK (status IN ('COMPLETED', 'PARTIALLY_FAILED', 'FAILED'))
);

CREATE TABLE bookmark_import_folders (
    id BIGSERIAL PRIMARY KEY,
    import_id BIGINT NOT NULL REFERENCES bookmark_imports(id) ON DELETE CASCADE,
    folder_id BIGINT NOT NULL,
    kind VARCHAR(10) NOT NULL,
    name VARCHAR(100) NOT NULL,
    reversed_at TIMESTAMP,
    created_at TIMESTAMP NOT NULL DEFAULT now(),
    CONSTRAINT bookmark_import_folders_kind_check CHECK (kind IN ('EXISTING', 'NEW'))
);

CREATE INDEX bookmark_import_folders_import_id_idx ON bookmark_import_folders(import_id);

CREATE TABLE bookmark_import_links (
    id BIGSERIAL PRIMARY KEY,
    import_id BIGINT NOT NULL REFERENCES bookmark_imports(id) ON DELETE CASCADE,
    link_id BIGINT NOT NULL,
    import_folder_id BIGINT REFERENCES bookmark_import_folders(id) ON DELETE CASCADE,
    url TEXT NOT NULL,
    title VARCHAR(300) NOT NULL,
    thumbnail_status VARCHAR(10) NOT NULL DEFAULT 'PENDING',
    started_at TIMESTAMP,
    reversed_at TIMESTAMP,
    created_at TIMESTAMP NOT NULL DEFAULT now(),
    updated_at TIMESTAMP NOT NULL DEFAULT now(),
    CONSTRAINT bookmark_import_links_thumbnail_status_check CHECK (thumbnail_status IN ('PENDING', 'DONE', 'FAILED', 'STOPPED'))
);

CREATE INDEX bookmark_import_links_import_id_idx ON bookmark_import_links(import_id);

CREATE TABLE bookmark_import_failures (
    id BIGSERIAL PRIMARY KEY,
    import_id BIGINT NOT NULL REFERENCES bookmark_imports(id) ON DELETE CASCADE,
    title TEXT NOT NULL,
    url TEXT NOT NULL,
    reason VARCHAR(20) NOT NULL,
    created_at TIMESTAMP NOT NULL DEFAULT now(),
    CONSTRAINT bookmark_import_failures_reason_check CHECK (reason IN ('URL_INVALID', 'TITLE_INVALID', 'UNKNOWN'))
);

CREATE INDEX bookmark_import_failures_import_id_idx ON bookmark_import_failures(import_id);

ALTER TABLE ai_jobs
    ADD COLUMN started_at TIMESTAMP;

CREATE INDEX links_owner_id_created_at_id_idx ON links(owner_id, created_at, id);
CREATE INDEX folders_owner_id_created_at_id_idx ON folders(owner_id, created_at, id);
