CREATE TABLE ai_providers (
    id BIGSERIAL PRIMARY KEY,
    type VARCHAR(10) NOT NULL,
    base_url VARCHAR(255) NOT NULL,
    created_at TIMESTAMP NOT NULL DEFAULT now(),
    updated_at TIMESTAMP NOT NULL DEFAULT now(),
    CONSTRAINT ai_providers_type_unique UNIQUE (type),
    CONSTRAINT ai_providers_type_check CHECK (type IN ('GEMINI', 'CLAUDE', 'OPENAI'))
);

CREATE TABLE available_models (
    id BIGSERIAL PRIMARY KEY,
    provider_id BIGINT NOT NULL REFERENCES ai_providers(id) ON DELETE CASCADE,
    model VARCHAR(100) NOT NULL,
    priority INT NOT NULL,
    rpd_limit INT,
    tpd_limit INT,
    created_at TIMESTAMP NOT NULL DEFAULT now(),
    updated_at TIMESTAMP NOT NULL DEFAULT now(),
    CONSTRAINT available_models_provider_model_unique UNIQUE (provider_id, model),
    CONSTRAINT available_models_priority_positive CHECK (priority > 0),
    CONSTRAINT available_models_rpd_limit_positive CHECK (rpd_limit IS NULL OR rpd_limit > 0),
    CONSTRAINT available_models_tpd_limit_positive CHECK (tpd_limit IS NULL OR tpd_limit > 0)
);

ALTER TABLE links
ADD COLUMN status VARCHAR(1) NOT NULL DEFAULT 'C',
ADD COLUMN work_model_id BIGINT REFERENCES available_models(id) ON DELETE SET NULL;

CREATE TABLE user_providers (
    id BIGSERIAL PRIMARY KEY,
    user_id BIGINT NOT NULL REFERENCES users(id) ON DELETE CASCADE,
    provider_id BIGINT NOT NULL REFERENCES ai_providers(id) ON DELETE CASCADE,
    user_role VARCHAR(20) NOT NULL,
    api_key VARCHAR(255) NOT NULL,
    created_at TIMESTAMP NOT NULL DEFAULT now(),
    updated_at TIMESTAMP NOT NULL DEFAULT now(),
    CONSTRAINT user_providers_user_provider_unique UNIQUE (user_id, provider_id),
    CONSTRAINT user_providers_user_role_check CHECK (user_role IN ('SUPER_ADMIN', 'ADMIN', 'NORMAL', 'GUEST'))
);

CREATE TABLE ai_jobs (
    id BIGSERIAL PRIMARY KEY,
    link_id BIGINT NOT NULL REFERENCES links(id) ON DELETE CASCADE,
    user_provider_id BIGINT NOT NULL REFERENCES user_providers(id) ON DELETE RESTRICT,
    request_model_id BIGINT NOT NULL REFERENCES available_models(id) ON DELETE RESTRICT,
    response_model_id BIGINT REFERENCES available_models(id) ON DELETE SET NULL,
    requested_url TEXT NOT NULL,
    prompt TEXT NOT NULL,
    response TEXT,
    status VARCHAR(1) NOT NULL DEFAULT 'P',
    requested_at TIMESTAMP NOT NULL DEFAULT now(),
    completed_at TIMESTAMP,
    created_at TIMESTAMP NOT NULL DEFAULT now(),
    updated_at TIMESTAMP NOT NULL DEFAULT now(),
    CONSTRAINT ai_jobs_status_check CHECK (status IN ('P', 'C', 'F'))
);

CREATE TABLE daily_usages (
    id BIGSERIAL PRIMARY KEY,
    user_provider_id BIGINT NOT NULL REFERENCES user_providers(id) ON DELETE CASCADE,
    model_id BIGINT NOT NULL REFERENCES available_models(id) ON DELETE CASCADE,
    usage_date DATE NOT NULL,
    requests INT NOT NULL DEFAULT 0,
    tokens INT NOT NULL DEFAULT 0,
    created_at TIMESTAMP NOT NULL DEFAULT now(),
    updated_at TIMESTAMP NOT NULL DEFAULT now(),
    CONSTRAINT daily_usages_user_provider_model_date_unique UNIQUE (user_provider_id, model_id, usage_date),
    CONSTRAINT daily_usages_requests_check CHECK (requests >= 0),
    CONSTRAINT daily_usages_tokens_check CHECK (tokens >= 0)
);

ALTER TABLE links
ADD CONSTRAINT links_status_check CHECK (status IN ('G', 'A', 'C', 'F'));

CREATE INDEX available_models_provider_id_idx ON available_models(provider_id);
CREATE INDEX links_status_idx ON links(status);
CREATE INDEX links_work_model_id_idx ON links(work_model_id);
CREATE INDEX user_providers_provider_id_idx ON user_providers(provider_id);
CREATE INDEX ai_jobs_link_id_idx ON ai_jobs(link_id);
CREATE INDEX ai_jobs_user_provider_id_idx ON ai_jobs(user_provider_id);
CREATE INDEX ai_jobs_request_model_id_idx ON ai_jobs(request_model_id);
CREATE INDEX ai_jobs_response_model_id_idx ON ai_jobs(response_model_id);
CREATE INDEX ai_jobs_status_idx ON ai_jobs(status);
CREATE INDEX daily_usages_model_id_idx ON daily_usages(model_id);
