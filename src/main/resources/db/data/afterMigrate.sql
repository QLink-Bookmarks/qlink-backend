SET
search_path TO qlink_local, public;

TRUNCATE TABLE users, folders RESTART IDENTITY CASCADE;

-- Users
INSERT INTO users (id, display_name, avatar_url, avatar_emoji, created_at, updated_at)
VALUES (1, '개발용 관리자', null, null, '2026-05-16T23:55:55Z', '2026-05-16T23:55:55Z');

-- Folders
INSERT INTO folders (id, owner_id, name, emoji, shared_at, created_at, updated_at)
VALUES (1, 1, '개발용 폴더', null, null, '2026-05-16T23:55:55Z', '2026-05-16T23:55:55Z');
