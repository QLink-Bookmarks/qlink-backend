CREATE TABLE posts (
    id BIGSERIAL PRIMARY KEY,
    title VARCHAR(100) NOT NULL,
    contents TEXT NOT NULL,
    type VARCHAR(20) NOT NULL,
    author_id BIGINT NOT NULL REFERENCES users(id) ON DELETE CASCADE,
    created_at TIMESTAMP NOT NULL DEFAULT now(),
    updated_at TIMESTAMP NOT NULL DEFAULT now(),
    CONSTRAINT posts_type_check CHECK (type IN ('ANNOUNCEMENT', 'FEEDBACK'))
);

CREATE INDEX posts_type_id_idx ON posts(type, id);
CREATE INDEX posts_author_id_idx ON posts(author_id);

CREATE TABLE post_images (
    id BIGSERIAL PRIMARY KEY,
    post_id BIGINT NOT NULL REFERENCES posts(id) ON DELETE CASCADE,
    url TEXT NOT NULL,
    created_at TIMESTAMP NOT NULL DEFAULT now()
);

CREATE INDEX post_images_post_id_idx ON post_images(post_id);

-- Announcements reuse the notifications table (context = ANNOUNCE), broadcast per user.
ALTER TABLE notifications DROP CONSTRAINT notifications_context_check;
ALTER TABLE notifications
    ADD CONSTRAINT notifications_context_check CHECK (context IN ('TODO', 'ANNOUNCE'));

-- Per-user uniqueness so one announcement (context_id = post id) can fan out to every user.
ALTER TABLE notifications DROP CONSTRAINT notifications_context_will_fire_unique;
ALTER TABLE notifications
    ADD CONSTRAINT notifications_user_context_will_fire_unique UNIQUE (user_id, context, context_id, will_fire_at);
