ALTER TABLE folder_members
ADD COLUMN user_name VARCHAR(50);

UPDATE folder_members fm
SET user_name = users.nickname
FROM users
WHERE users.id = fm.user_id
  AND fm.user_name IS NULL;

ALTER TABLE folder_members
ALTER COLUMN user_name SET NOT NULL;
