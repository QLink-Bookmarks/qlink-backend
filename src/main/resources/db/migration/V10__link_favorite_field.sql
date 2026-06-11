ALTER TABLE links
ADD COLUMN favorite_at TIMESTAMP;

CREATE INDEX links_favorite_at_idx ON links(favorite_at);
