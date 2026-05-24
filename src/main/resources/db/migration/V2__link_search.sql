CREATE EXTENSION IF NOT EXISTS pg_trgm;

ALTER TABLE links
ADD COLUMN search_text TEXT NOT NULL DEFAULT '';

UPDATE links
SET search_text =
    coalesce(title, '') || ' ' ||
    coalesce(url, '') || ' ' ||
    array_to_string(coalesce(tags, '{}'), ' ') || ' ' ||
    coalesce(summary, '') || ' ' ||
    coalesce(memo, '');

CREATE INDEX links_search_text_idx
ON links
USING GIN (search_text gin_trgm_ops);
