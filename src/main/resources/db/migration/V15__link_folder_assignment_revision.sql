SET LOCAL lock_timeout = '5s';

ALTER TABLE links
ADD COLUMN folder_assignment_revision BIGINT NOT NULL DEFAULT 0;

CREATE FUNCTION increment_link_folder_assignment_revision()
RETURNS TRIGGER AS $$
BEGIN
    IF NEW.folder_id IS DISTINCT FROM OLD.folder_id THEN
        NEW.folder_assignment_revision := OLD.folder_assignment_revision + 1;
    ELSE
        NEW.folder_assignment_revision := OLD.folder_assignment_revision;
    END IF;
    RETURN NEW;
END;
$$ LANGUAGE plpgsql;

CREATE TRIGGER links_folder_assignment_revision
BEFORE UPDATE ON links
FOR EACH ROW
EXECUTE FUNCTION increment_link_folder_assignment_revision();
