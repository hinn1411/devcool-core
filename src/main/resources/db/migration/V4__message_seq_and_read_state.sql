-- P3-T02: seq, idempotency key, edit version, read state (ADR-0012). Expand only: seq stays
-- nullable (old tasks insert without it); P3-T03a sets NOT NULL.

set local lock_timeout = '5s';

-- Constant defaults: metadata-only, no rewrite; old inserts still pass.
alter table channel
    add column last_seq        bigint not null default 0,
    add column last_message_at timestamp(6) with time zone;

alter table message
    add column seq           bigint,
    add column client_msg_id uuid,
    add column version       integer not null default 0;

alter table member
    add column last_read_seq bigint not null default 0;

-- Backfill. 1. seq 1..n per channel, in id order (today's history order)
update message
set seq = r.rn
from (
    select id, row_number() over (partition by channel_id order by id) as rn
    from message) as r
where message.id = r.id;

-- 2. Empty channels have no row in m, so they keep the defaults (0, NULL).
update channel
set last_seq = m.max_seq, last_message_at = m.latest
from (
    select channel_id, max(seq) as max_seq, max(created_time) as latest
    from message
    group by channel_id) as m
where channel.id = m.channel_id;

-- 3. Existing history counts as read (after 2).
update member
set last_read_seq = channel.last_seq
from channel
where member.channel_id = channel.id;

-- Unique keys after the backfill: each index built once; NULLs don't collide.
-- (channel_id, seq) also serves history pages, scanned backwards.
alter table message
    add constraint uk_message_channel_seq unique (channel_id, seq),
    add constraint uk_message_user_client_msg unique (user_id, client_msg_id);
