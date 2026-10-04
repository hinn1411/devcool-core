-- Message history seed (P1-T03; reused by playbook §5.2 game day 4, "slow history page").
-- Run on a FRESH local DB (after Flyway; the ids below are fixed and collide with existing rows):
--   docker compose -f docker/local/compose.yaml exec -T postgres \
--     sh -c 'psql -v ON_ERROR_STOP=1 -U "$POSTGRES_USER" -d "$POSTGRES_DB"' < docker/local/seed/message-history-seed.sql
-- Then run message-history-explain.sql the same way.
--
-- Channel 1 is hot (every other message, 50k). Channels 2-20 share the rest.
-- Channel 21 is quiet and old: 100 messages among the oldest ids, the worst case for a backward PK walk.
insert into app_user (id, user_name, email, name, role, status, token_version)
select i, 'seed' || i, 'seed' || i || '@example.test', 'Seed ' || i, 'USER', 'ACTIVE', 0
from generate_series(1, 20) i;

insert into channel (id, name, boundary, total_of_members, channel_type, creator_id, leader_id)
select i, 'seed-channel-' || i, 'PUBLIC', 20, 'LOUNGE', 1, 1
from generate_series(1, 21) i;

insert into message (id, user_id, channel_id, content, content_type, created_time, deleted_time)
select i,
       (i % 20) + 1,
       case when i <= 200 and i % 2 = 1 then 21
            when i % 2 = 0 then 1
            else (i % 19) + 2 end,
       'message ' || i,
       'TEXT',
       now() - make_interval(secs => 100000 - i),
       case when i % 50 = 0 then now() end
from generate_series(1, 100000) i;

select setval('app_user_seq', 1000), setval('channel_seq', 1000), setval('message_seq', 200000);
analyze app_user;
analyze channel;
analyze message;
analyze media;

select channel_id, count(*) from message where channel_id in (1, 2, 21) group by channel_id order by channel_id;
