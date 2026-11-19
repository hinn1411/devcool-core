-- flyway:executeInTransaction=false
-- P3-T13: GIN for `@@`. CONCURRENTLY: hot table.

create index concurrently if not exists ix_message_search_vector
    on message using gin (search_vector);
