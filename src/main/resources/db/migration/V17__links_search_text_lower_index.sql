-- 비트랜잭션 마이그레이션(V17__links_search_text_lower_index.sql.conf: executeInTransaction=false).
-- 앞선 실패로 남은 INVALID 인덱스를 먼저 치운 뒤 다시 만든다. 실패 시 `flyway repair` → 재기동.
DROP INDEX CONCURRENTLY IF EXISTS links_search_text_lower_bigm_idx;

CREATE INDEX CONCURRENTLY links_search_text_lower_bigm_idx
    ON links USING GIN (lower(search_text) public.gin_bigm_ops);
