-- flyway:executeInTransaction=false
-- P3-T13: GIN maps each word to the rows that contain it, so `search_vector @@ query` reads only
-- the matching rows. CONCURRENTLY: the send path writes to message all the time.

create index concurrently if not exists ix_message_search_vector
    on message using gin (search_vector);
