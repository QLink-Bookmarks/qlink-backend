CREATE EXTENSION IF NOT EXISTS pg_bigm;

ALTER TABLE links
ADD COLUMN search_text TEXT NOT NULL DEFAULT '';

UPDATE links
SET search_text =
    coalesce(title, '') || ' ' ||
    coalesce(url, '') || ' ' ||
    array_to_string(coalesce(tags, '{}'), ' ') || ' ' ||
    coalesce(summary, '') || ' ' ||
    coalesce(memo, '');
