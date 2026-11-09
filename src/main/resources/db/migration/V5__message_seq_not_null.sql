-- P3-T03a: contract for V4 (ADR-0012). Runs only after every task writes seq (P3-T03 deployed),
-- so no new NULL arrives. Rows that old tasks wrote with a NULL seq are appended after each
-- channel's last_seq in id order; seqs that clients have already seen never move.
-- On a large table, use CHECK (seq IS NOT NULL) NOT VALID -> VALIDATE -> SET NOT NULL instead.

set local lock_timeout = '5s';

-- One statement, same lock order as a send: the channel row is bumped (and locked until commit)
-- first, and its seqs are numbered from the pre-bump value. Channels without NULL rows are not
-- touched.
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
    select id, channel_id,  row_number() over (partition by channel_id order by id) as rn
    from message
    where message.seq is null
    ) as r
join bumped on bumped.channel_id = r.channel_id
where message.id = r.id
;

alter table message alter column seq set not null;
