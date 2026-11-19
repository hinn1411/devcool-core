-- P3-T13: 'simple' = no stemming/stopwords.
-- STORED rewrites the table once (fine at current size).

alter table message
    add column search_vector tsvector
        generated always as (to_tsvector('simple', coalesce(content, ''))) stored;
