-- EXPLAIN for the history query, with and without ix_message_channel_id_id (P1-T03).
-- Needs message-history-seed.sql loaded. "Before" drops the index inside a transaction that is rolled back.
-- Run:
--   docker compose -f docker/local/compose.yaml exec -T postgres \
--     sh -c 'psql -U "$POSTGRES_USER" -d "$POSTGRES_DB"' < docker/local/seed/message-history-explain.sql
-- History SQL as MessageRepository.findByChannelId issues it (page size 50 + 1).
-- Literal values give the custom plan; PREPARE + force_generic_plan shows the cached generic plan.
\pset pager off
\echo '=== AFTER: hot channel 1, first page'
explain (analyze, buffers, costs off)
select m.id, m.content, u.name, md.path
from message m left join media md on md.message_id = m.id join app_user u on u.id = m.user_id
where m.channel_id = 1 and m.deleted_time is null and (null::int is null or m.id < null::int)
order by m.id desc fetch first 51 rows only;

\echo '=== AFTER: hot channel 1, cursor page (id < 20000)'
explain (analyze, buffers, costs off)
select m.id, m.content, u.name, md.path
from message m left join media md on md.message_id = m.id join app_user u on u.id = m.user_id
where m.channel_id = 1 and m.deleted_time is null and (20000 is null or m.id < 20000)
order by m.id desc fetch first 51 rows only;

\echo '=== AFTER: quiet channel 21, first page'
explain (analyze, buffers, costs off)
select m.id, m.content, u.name, md.path
from message m left join media md on md.message_id = m.id join app_user u on u.id = m.user_id
where m.channel_id = 21 and m.deleted_time is null and (null::int is null or m.id < null::int)
order by m.id desc fetch first 51 rows only;

\echo '=== AFTER: GENERIC plan, hot channel 1, cursor page (id < 20000)'
prepare history(int, int, int) as
select m.id, m.content, u.name, md.path
from message m left join media md on md.message_id = m.id join app_user u on u.id = m.user_id
where m.channel_id = $1 and m.deleted_time is null and ($2::int is null or m.id < $2)
order by m.id desc fetch first $3 rows only;
set plan_cache_mode = force_generic_plan;
explain (analyze, buffers, costs off) execute history(1, 20000, 51);
reset plan_cache_mode;
deallocate history;

begin;
drop index ix_message_channel_id_id;

\echo '=== BEFORE: hot channel 1, first page'
explain (analyze, buffers, costs off)
select m.id, m.content, u.name, md.path
from message m left join media md on md.message_id = m.id join app_user u on u.id = m.user_id
where m.channel_id = 1 and m.deleted_time is null and (null::int is null or m.id < null::int)
order by m.id desc fetch first 51 rows only;

\echo '=== BEFORE: hot channel 1, cursor page (id < 20000)'
explain (analyze, buffers, costs off)
select m.id, m.content, u.name, md.path
from message m left join media md on md.message_id = m.id join app_user u on u.id = m.user_id
where m.channel_id = 1 and m.deleted_time is null and (20000 is null or m.id < 20000)
order by m.id desc fetch first 51 rows only;

\echo '=== BEFORE: quiet channel 21, first page'
explain (analyze, buffers, costs off)
select m.id, m.content, u.name, md.path
from message m left join media md on md.message_id = m.id join app_user u on u.id = m.user_id
where m.channel_id = 21 and m.deleted_time is null and (null::int is null or m.id < null::int)
order by m.id desc fetch first 51 rows only;
rollback;
