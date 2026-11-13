-- P3-T09: one row per (message, user, emoji); the PK is the toggle's idempotency key
-- (INSERT … ON CONFLICT DO NOTHING). New table, so no expand/contract. No extra index: the
-- per-page summary filters on message_id, the PK's leading column.
-- emoji varchar(16): Postgres counts characters (code points), matching Emoji.MAX_CODE_POINTS.

set local lock_timeout = '5s';

create table message_reaction
(
    message_id   bigint                      not null
        constraint fk_message_reaction_message references message (id),
    user_id      bigint                      not null
        constraint fk_message_reaction_user references app_user (id),
    emoji        varchar(16)                 not null,
    created_time timestamp(6) with time zone not null,
    constraint pk_message_reaction primary key (message_id, user_id, emoji)
);
