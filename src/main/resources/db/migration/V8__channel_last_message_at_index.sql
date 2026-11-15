-- flyway:executeInTransaction=false
-- P3-T10: the channel list sorts by last_message_at DESC NULLS LAST, id DESC (keyset pagination).
-- Matches that order so a member with many channels can read in index order. CONCURRENTLY: the
-- channel table exists in deployed databases, and the send path updates it on every message.

create index concurrently if not exists ix_channel_last_message_at_id
    on channel (last_message_at desc nulls last, id desc);
