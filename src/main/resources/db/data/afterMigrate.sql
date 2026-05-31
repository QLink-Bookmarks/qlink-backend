SET
search_path TO qlink_local, public;

TRUNCATE TABLE users, folders, ai_providers, available_models, user_providers RESTART IDENTITY CASCADE;

-- Users
INSERT INTO users (id, display_name, avatar_url, avatar_emoji, created_at, updated_at)
VALUES (1, '개발용 관리자', null, null, '2026-05-16T23:55:55Z', '2026-05-16T23:55:55Z');

-- Folders
INSERT INTO folders (id, owner_id, name, emoji, shared_at, created_at, updated_at)
VALUES (1, 1, '개발용 폴더', '👨🏻‍💻', null, '2026-05-16T23:55:55Z', '2026-05-16T23:55:55Z');

-- AI Providers
INSERT INTO ai_providers (id, type, base_url, created_at, updated_at)
VALUES
    (1, 'GEMINI', 'https://generativelanguage.googleapis.com/v1beta', '2026-05-16T23:55:55Z', '2026-05-16T23:55:55Z'),
    (2, 'OPENAI', 'https://api.openai.com/v1/responses', '2026-05-16T23:55:55Z', '2026-05-16T23:55:55Z');

-- Available Models
INSERT INTO available_models (id, provider_id, model, priority, rpd_limit, tpd_limit, created_at, updated_at)
VALUES
    (1, 1, 'gemini-3.5-flash', 1, 20, null, '2026-05-16T23:55:55Z', '2026-05-16T23:55:55Z'),
    (2, 1, 'gemini-3-flash-preview', 2, 20, null, '2026-05-16T23:55:55Z', '2026-05-16T23:55:55Z'),
    (3, 1, 'gemini-3.1-flash-lite', 3, 500, null, '2026-05-16T23:55:55Z', '2026-05-16T23:55:55Z'),
    (4, 2, 'gpt-5.5', 1, 20, 2000000, '2026-05-16T23:55:55Z', '2026-05-16T23:55:55Z'),
    (5, 2, 'gpt-5.4', 2, 20, 2000000, '2026-05-16T23:55:55Z', '2026-05-16T23:55:55Z'),
    (6, 2, 'gpt-5.4-mini', 3, 500, 50000000, '2026-05-16T23:55:55Z', '2026-05-16T23:55:55Z');

-- User Providers
INSERT INTO user_providers (id, user_id, provider_id, user_role, api_key, created_at, updated_at)
VALUES
    (1, 1, 1, 'SUPER_ADMIN', 'GEMINI_API_KEY_PLACEHOLDER', '2026-05-16T23:55:55Z', '2026-05-16T23:55:55Z'),
    (2, 1, 2, 'SUPER_ADMIN', 'OPENAI_API_KEY_PLACEHOLDER', '2026-05-16T23:55:55Z', '2026-05-16T23:55:55Z');
