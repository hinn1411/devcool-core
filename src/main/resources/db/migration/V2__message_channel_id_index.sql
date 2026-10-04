-- Index for channel history pagination (P1-T03): MessageRepository.findByChannelId filters on
-- channel_id and orders by id DESC. V1 has no secondary indexes, so that query scans every channel.
-- Plain CREATE INDEX (not CONCURRENTLY): no deployed database exists yet, so the build lock is harmless.
-- Replaced by (channel_id, seq DESC) in P3-T02 once history moves to per-channel seq (ADR-0012).

create index ix_message_channel_id_id
    on message (channel_id, id desc);
