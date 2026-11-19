-- P3-T13: keyword search over message text. 'simple' = lowercase + split only: no stemming and no
-- stopwords, so mixed-language chat matches whole words as typed. STORED: words are split once on
-- write, not per query. Old code never names the column, so rolling deploys are unaffected.
-- Adding a stored generated column rewrites the table once under an exclusive lock; acceptable at
-- today's size. On a large table, use a nullable column + batched backfill + trigger instead.

alter table message
    add column search_vector tsvector
        generated always as (to_tsvector('simple', coalesce(content, ''))) stored;
