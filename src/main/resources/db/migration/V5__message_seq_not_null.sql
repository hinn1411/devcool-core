-- P3-T03a: contract for V4 (ADR-0012). Append NULL seqs after last_seq, then NOT NULL.
-- Large table: CHECK NOT VALID -> VALIDATE -> SET NOT NULL instead.

set local lock_timeout = '5s';

-- Same lock order as a send: bump channel.last_seq first, number from the old value.
-- Assumes last_seq >= max(seq); otherwise the unique key fails and V5 rolls back.
with nulls as (
    select channel_id, count(*) as n
    from message
    where seq is null
    group by channel_id),
bumped as (
    update channel
    set last_seq = channel.last_seq + nulls.n
    from nulls
    where channel.id = nulls.channel_id
    returning channel.id as channel_id, channel.last_seq - nulls.n as base)
update message
set seq = bumped.base + r.rn
from (
    select id, channel_id, row_number() over (partition by channel_id order by id) as rn
    from message
    where message.seq is null) as r
join bumped on bumped.channel_id = r.channel_id
where message.id = r.id;

alter table message alter column seq set not null;
