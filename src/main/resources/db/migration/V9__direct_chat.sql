-- P3-T12: one PRIVATE_CHAT per unordered user pair. Low id first, so A→B and B→A share the key.
create table direct_chat (
    user_low_id  bigint not null,
    user_high_id bigint not null,
    channel_id   bigint not null,
    constraint pk_direct_chat primary key (user_low_id, user_high_id),
    constraint uk_direct_chat_channel unique (channel_id),
    constraint ck_direct_chat_order check (user_low_id < user_high_id),
    constraint fk_direct_chat_low foreign key (user_low_id) references app_user (id),
    constraint fk_direct_chat_high foreign key (user_high_id) references app_user (id),
    constraint fk_direct_chat_channel foreign key (channel_id) references channel (id)
);

-- Backfill: the oldest (lowest id) PRIVATE_CHAT per pair. Later duplicates stay as plain channels;
-- merging their messages is out of scope. Only chats with exactly two distinct members count.
-- DISTINCT ON picks the winner explicitly; insert order under ON CONFLICT isn't guaranteed.
insert into direct_chat (user_low_id, user_high_id, channel_id)
select distinct on (pair.low, pair.high) pair.low, pair.high, pair.channel_id
from (
    select min(m.user_id) as low, max(m.user_id) as high, c.id as channel_id
    from channel c
    join member m on m.channel_id = c.id
    where c.channel_type = 'PRIVATE_CHAT'
    group by c.id
    having count(distinct m.user_id) = 2
) pair
order by pair.low, pair.high, pair.channel_id;
