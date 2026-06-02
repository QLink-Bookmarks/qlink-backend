ALTER TABLE todos
    ADD COLUMN repeat_until TIMESTAMP,
    ADD COLUMN repeat_days VARCHAR(100),
    ADD COLUMN repeat_time TIME,
    ADD COLUMN repeat_timezone VARCHAR(100);
