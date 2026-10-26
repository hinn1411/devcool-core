-- P3-T01: every PK/FK INTEGER -> BIGINT (no overflow at 2^31). Ships with the Long entities (ddl-auto=validate).

-- REVIEW (lock): each ALTER rewrites its table under ACCESS EXCLUSIVE. SET LOCAL keeps the timeout
-- in this transaction; past 5 s the migrate task fails and deploy.yml stops before rollout.
set local lock_timeout = '5s';

-- REVIEW (no expand/contract): running old tasks keep working (bigint < 2^31 reads as int).
-- Restarting the old image fails validate, so after V3 the only way back is a fix deployed forward.
-- REVIEW (FKs): one ALTER per table = one rewrite; Postgres rebuilds and rechecks FKs in one transaction.
-- REVIEW (sequences): already bigint (V1 has no AS clause); Hibernate pooled 50 unchanged (ADR-0012).
-- REVIEW (scope): refresh_token.user_id stays VARCHAR, no FK (brief P3-T01 debt; can't overflow).

alter table app_user
    alter column id type bigint;

alter table auth_provider
    alter column id      type bigint,
    alter column user_id type bigint;

alter table channel
    alter column id         type bigint,
    alter column creator_id type bigint,
    alter column leader_id  type bigint;

alter table friend_request
    alter column id          type bigint,
    alter column sender_id   type bigint,
    alter column receiver_id type bigint;

alter table member
    alter column id         type bigint,
    alter column user_id    type bigint,
    alter column channel_id type bigint;

alter table message
    alter column id         type bigint,
    alter column user_id    type bigint,
    alter column channel_id type bigint;

alter table media
    alter column id         type bigint,
    alter column message_id type bigint;

alter table topic
    alter column id type bigint;

alter table topics_of_channels
    alter column topic_id   type bigint,
    alter column channel_id type bigint;
