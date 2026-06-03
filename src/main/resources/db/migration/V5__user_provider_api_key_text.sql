ALTER TABLE user_providers
ALTER COLUMN api_key TYPE TEXT;

ALTER TABLE todos
    ADD COLUMN repeat_until TIMESTAMP,
    ADD COLUMN repeat_days VARCHAR(3)[],
    ADD COLUMN repeat_time TIME WITHOUT TIME ZONE,
    ADD COLUMN repeat_timezone VARCHAR(100);
