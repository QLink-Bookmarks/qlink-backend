ALTER TABLE users
ADD COLUMN username VARCHAR(100),
ADD COLUMN nickname VARCHAR(50),
ADD COLUMN role VARCHAR(20) NOT NULL DEFAULT 'NORMAL',
ADD COLUMN theme VARCHAR(1) NOT NULL DEFAULT 'L',
ADD COLUMN accent VARCHAR(1) NOT NULL DEFAULT 'G',
ADD COLUMN allows_reminder BOOLEAN NOT NULL DEFAULT true,
ADD COLUMN default_ai_provider_id BIGINT REFERENCES ai_providers(id) ON DELETE SET NULL,
ADD COLUMN default_model_id BIGINT REFERENCES available_models(id) ON DELETE SET NULL;

UPDATE users
SET
    username = 'user_' || id,
    nickname = display_name
WHERE username IS NULL
   OR nickname IS NULL;

ALTER TABLE users
ALTER COLUMN username SET NOT NULL,
ALTER COLUMN nickname SET NOT NULL;

ALTER TABLE users
DROP COLUMN display_name;

ALTER TABLE users
ADD CONSTRAINT users_username_unique UNIQUE (username),
ADD CONSTRAINT users_role_check CHECK (role IN ('SUPER_ADMIN', 'ADMIN', 'NORMAL', 'GUEST')),
ADD CONSTRAINT users_theme_check CHECK (theme IN ('D', 'L')),
ADD CONSTRAINT users_accent_check CHECK (accent IN ('G', 'P', 'B'));

CREATE INDEX users_default_ai_provider_id_idx ON users(default_ai_provider_id);
CREATE INDEX users_default_model_id_idx ON users(default_model_id);
