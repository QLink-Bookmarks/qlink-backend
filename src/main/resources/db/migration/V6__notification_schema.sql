CREATE TABLE notifications (
    id BIGSERIAL PRIMARY KEY,
    user_id BIGINT NOT NULL REFERENCES users(id) ON DELETE CASCADE,
    title VARCHAR(50) NOT NULL,
    message VARCHAR(200) NOT NULL,
    context VARCHAR(20) NOT NULL,
    context_id BIGINT NOT NULL,
    will_fire_at TIMESTAMP NOT NULL,
    scheduled_at TIMESTAMP,
    fired_at TIMESTAMP,
    failed_at TIMESTAMP,
    success_count INT NOT NULL DEFAULT 0,
    failure_count INT NOT NULL DEFAULT 0,
    created_at TIMESTAMP NOT NULL DEFAULT now(),
    updated_at TIMESTAMP NOT NULL DEFAULT now(),
    CONSTRAINT notifications_context_check CHECK (context IN ('TODO')),
    CONSTRAINT notifications_counts_check CHECK (success_count >= 0 AND failure_count >= 0),
    CONSTRAINT notifications_context_will_fire_unique UNIQUE (context, context_id, will_fire_at)
);

CREATE INDEX notifications_user_id_idx ON notifications(user_id);
CREATE INDEX notifications_will_fire_at_idx ON notifications(will_fire_at);
CREATE INDEX notifications_context_idx ON notifications(context, context_id);
CREATE INDEX notifications_pending_will_fire_at_idx
ON notifications(will_fire_at)
WHERE fired_at IS NULL AND failed_at IS NULL;
