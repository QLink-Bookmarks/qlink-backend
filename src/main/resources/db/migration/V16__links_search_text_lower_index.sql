DROP INDEX CONCURRENTLY IF EXISTS links_search_text_lower_bigm_idx;

CREATE INDEX CONCURRENTLY links_search_text_lower_bigm_idx
ON links
USING GIN (lower(search_text) public.gin_bigm_ops);
