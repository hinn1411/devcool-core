-- P3-T08: a reply points at a message of the same channel (checked in MessageService).
-- Expand only: nullable, no backfill. No index: reads join the parent by its PK and nothing
-- hard-deletes messages yet (revisit with P6 erase).

set local lock_timeout = '5s';

alter table message
    add column reply_to_id bigint
        constraint fk_message_reply_to references message (id);
