CREATE TABLE ai_providers (
    id BIGSERIAL PRIMARY KEY,
    name VARCHAR(50) NOT NULL,
    created_at TIMESTAMP NOT NULL DEFAULT now(),
    updated_at TIMESTAMP NOT NULL DEFAULT now(),
    CONSTRAINT ai_providers_name_unique UNIQUE (name)
);

CREATE TABLE available_models (
    id BIGSERIAL PRIMARY KEY,
    provider_id BIGINT NOT NULL REFERENCES ai_providers(id) ON DELETE CASCADE,
    model_key VARCHAR(100) NOT NULL,
    display_name VARCHAR(100) NOT NULL,
    created_at TIMESTAMP NOT NULL DEFAULT now(),
    updated_at TIMESTAMP NOT NULL DEFAULT now(),
    CONSTRAINT available_models_provider_model_unique UNIQUE (provider_id, model_key)
);

ALTER TABLE links
ADD COLUMN status VARCHAR(1) NOT NULL DEFAULT 'A',
ADD COLUMN work_model_id BIGINT REFERENCES available_models(id) ON DELETE SET NULL;

CREATE TABLE user_providers (
    id BIGSERIAL PRIMARY KEY,
    user_id BIGINT NOT NULL REFERENCES users(id) ON DELETE CASCADE,
    provider_id BIGINT NOT NULL REFERENCES ai_providers(id) ON DELETE CASCADE,
    role VARCHAR(20) NOT NULL,
    created_at TIMESTAMP NOT NULL DEFAULT now(),
    updated_at TIMESTAMP NOT NULL DEFAULT now(),
    CONSTRAINT user_providers_user_provider_unique UNIQUE (user_id, provider_id),
    CONSTRAINT user_providers_role_check CHECK (role IN ('SUPER_ADMIN', 'ADMIN', 'NORMAL', 'GUEST'))
);

CREATE TABLE ai_jobs (
    id BIGSERIAL PRIMARY KEY,
    link_id BIGINT NOT NULL REFERENCES links(id) ON DELETE CASCADE,
    owner_id BIGINT NOT NULL REFERENCES users(id) ON DELETE CASCADE,
    model_id BIGINT NOT NULL REFERENCES available_models(id) ON DELETE RESTRICT,
    status VARCHAR(1) NOT NULL DEFAULT 'P',
    error_message TEXT,
    created_at TIMESTAMP NOT NULL DEFAULT now(),
    updated_at TIMESTAMP NOT NULL DEFAULT now(),
    CONSTRAINT ai_jobs_status_check CHECK (status IN ('P', 'C', 'F'))
);

CREATE TABLE daily_usages (
    id BIGSERIAL PRIMARY KEY,
    user_id BIGINT NOT NULL REFERENCES users(id) ON DELETE CASCADE,
    provider_id BIGINT NOT NULL REFERENCES ai_providers(id) ON DELETE CASCADE,
    usage_date DATE NOT NULL,
    request_count INT NOT NULL DEFAULT 0,
    created_at TIMESTAMP NOT NULL DEFAULT now(),
    updated_at TIMESTAMP NOT NULL DEFAULT now(),
    CONSTRAINT daily_usages_user_provider_date_unique UNIQUE (user_id, provider_id, usage_date),
    CONSTRAINT daily_usages_request_count_check CHECK (request_count >= 0)
);

ALTER TABLE links
ADD CONSTRAINT links_status_check CHECK (status IN ('G', 'A', 'C', 'F'));

CREATE INDEX available_models_provider_id_idx ON available_models(provider_id);
CREATE INDEX links_status_idx ON links(status);
CREATE INDEX links_work_model_id_idx ON links(work_model_id);
CREATE INDEX user_providers_provider_id_idx ON user_providers(provider_id);
CREATE INDEX ai_jobs_link_id_idx ON ai_jobs(link_id);
CREATE INDEX ai_jobs_owner_id_idx ON ai_jobs(owner_id);
CREATE INDEX ai_jobs_model_id_idx ON ai_jobs(model_id);
CREATE INDEX ai_jobs_status_idx ON ai_jobs(status);
CREATE INDEX daily_usages_provider_id_idx ON daily_usages(provider_id);
