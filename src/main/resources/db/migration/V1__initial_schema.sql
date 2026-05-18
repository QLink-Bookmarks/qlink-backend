CREATE TABLE users (
    id BIGSERIAL PRIMARY KEY,
    display_name VARCHAR(50) NOT NULL,
    avatar_url VARCHAR(500),
    avatar_emoji VARCHAR(20),
    created_at TIMESTAMP NOT NULL DEFAULT now(),
    updated_at TIMESTAMP NOT NULL DEFAULT now()
);

CREATE TABLE auth_providers (
    id BIGSERIAL PRIMARY KEY,
    user_id BIGINT NOT NULL REFERENCES users(id) ON DELETE CASCADE,
    provider_type VARCHAR(20) NOT NULL,
    provider_id VARCHAR(255) NOT NULL,
    created_at TIMESTAMP NOT NULL DEFAULT now(),
    updated_at TIMESTAMP NOT NULL DEFAULT now(),
    CONSTRAINT auth_providers_provider_unique UNIQUE (provider_type, provider_id)
);

CREATE TABLE folders (
    id BIGSERIAL PRIMARY KEY,
    owner_id BIGINT NOT NULL REFERENCES users(id) ON DELETE CASCADE,
    name VARCHAR(100) NOT NULL,
    emoji VARCHAR(20),
    shared_at TIMESTAMP,
    created_at TIMESTAMP NOT NULL DEFAULT now(),
    updated_at timestamp NOT NULL DEFAULT now()
);

CREATE TABLE folder_members (
    folder_id BIGINT NOT NULL REFERENCES folders(id) ON DELETE CASCADE,
    user_id BIGINT NOT NULL REFERENCES users(id) ON DELETE CASCADE,
    role VARCHAR(15) NOT NULL,
    joined_at timestamp NOT NULL DEFAULT now(),
    created_at timestamp NOT NULL DEFAULT now(),
    updated_at timestamp NOT NULL DEFAULT now(),
    PRIMARY KEY (folder_id, user_id)
);

CREATE TABLE links (
    id BIGSERIAL PRIMARY KEY,
    owner_id BIGINT NOT NULL REFERENCES users(id) ON DELETE CASCADE,
    folder_id BIGINT REFERENCES folders(id) ON DELETE SET NULL,
    url TEXT NOT NULL,
    title VARCHAR(300) NOT NULL,
    summary TEXT,
    memo TEXT,
    tags TEXT[] NOT NULL DEFAULT '{}',
    thumbnail_url TEXT,
    source_type VARCHAR(30) NOT NULL,
    created_at TIMESTAMP NOT NULL DEFAULT now(),
    updated_at TIMESTAMP NOT NULL DEFAULT now()
);

CREATE TABLE folder_invites (
    id BIGSERIAL PRIMARY KEY,
    folder_id BIGINT NOT NULL REFERENCES folders(id) ON DELETE CASCADE,
    inviter_id BIGINT NOT NULL REFERENCES users(id) ON DELETE CASCADE,
    token VARCHAR(64) NOT NULL,
    expires_at timestamp NOT NULL,
    accepted_at timestamp,
    created_at timestamp NOT NULL DEFAULT now(),
    updated_at timestamp NOT NULL DEFAULT now(),
    CONSTRAINT folder_invites_token_unique UNIQUE (token)
);

CREATE TABLE todos (
    id BIGSERIAL PRIMARY KEY,
    link_id BIGINT NOT NULL REFERENCES links(id) ON DELETE CASCADE,
    owner_id BIGINT NOT NULL REFERENCES users(id) ON DELETE CASCADE,
    title VARCHAR(50) NOT NULL,
    reminder_at TIMESTAMP,
    completed_at TIMESTAMP,
    created_at timestamp NOT NULL DEFAULT now(),
    updated_at timestamp NOT NULL DEFAULT now()
);

CREATE INDEX auth_providers_user_id_idx ON auth_providers(user_id);
CREATE INDEX folders_owner_id_idx ON folders(owner_id);
CREATE INDEX folder_members_user_id_idx ON folder_members(user_id);
CREATE INDEX links_owner_id_idx ON links(owner_id);
CREATE INDEX links_folder_id_idx ON links(folder_id);
CREATE INDEX links_tags_idx ON links USING GIN (tags);
CREATE INDEX folder_invites_folder_id_idx ON folder_invites(folder_id);
CREATE INDEX folder_invites_inviter_id_idx ON folder_invites(inviter_id);
CREATE INDEX todos_link_id_idx ON todos(link_id);
CREATE INDEX todos_owner_id_idx ON todos(owner_id);
