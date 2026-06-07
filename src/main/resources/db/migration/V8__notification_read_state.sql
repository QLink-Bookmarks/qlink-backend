ALTER TABLE notifications
ADD COLUMN read_at TIMESTAMP;

CREATE INDEX notifications_user_read_idx
ON notifications(user_id, id DESC)
WHERE fired_at IS NOT NULL;

CREATE INDEX notifications_unread_user_idx
ON notifications(user_id)
WHERE fired_at IS NOT NULL AND read_at IS NULL;
