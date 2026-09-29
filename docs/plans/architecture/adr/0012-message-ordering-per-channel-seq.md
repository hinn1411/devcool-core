# 0012 — Per-channel sequence numbers

- **Status:** Accepted
- **Date:** 2026-09-29
- **Phase:** P3

## Context
Messages have an `INTEGER` id from a global DB sequence. The WS push carries neither id nor time. Clients can't order pushes, detect gaps, or resume after a disconnect. The cursor pagination uses `id < cursorId`, which works for history but not for "everything after what I have".

## Decision drivers
- Clients must detect missed messages and fetch exactly those.
- Total order within a channel (what users see); no need for global order.
- Idempotent retries must return the same position.

## Options considered

| Option | Order | Gap detection | Contention | Notes |
|---|---|---|---|---|
| `created_time` | approximate | ✗ | none | Clock skew across tasks; ties; can't tell "missing" from "none sent" |
| Global DB sequence / BIGSERIAL (today) | global, commit order ≠ id order | ✗ (ids skip across channels) | none | Good as a primary key, useless for per-channel gaps |
| Snowflake / ULID | roughly time-ordered | ✗ | none | Great for distributed id generation; still no gap detection |
| Redis `INCR chat:seq:{channel}` | per channel | ✓ | none in DB | Second system; a Redis failover can reuse numbers; must reconcile with the DB |
| **`channel.last_seq` bumped in the send transaction** | **per channel, commit order** | **✓** | row lock per channel | Serializes writers of one channel only |

## Decision
Keep `message.id BIGINT` (identity) as the primary key and global reference (citations, replies). Add `message.seq BIGINT NOT NULL` with `UNIQUE (channel_id, seq)`, assigned by:

```sql
UPDATE channel SET last_seq = last_seq + 1, last_message_at = now()
 WHERE id = :channelId RETURNING last_seq;
```

in the same transaction as the insert and the outbox row.

- History: `WHERE channel_id = ? AND seq < :beforeSeq ORDER BY seq DESC LIMIT n`.
- Resume: `WHERE channel_id = ? AND seq > :afterSeq ORDER BY seq ASC LIMIT 200`.
- Unread: `channel.last_seq − member.last_read_seq`.

## Consequences
- **Hot-channel ceiling:** one commit at a time per channel (~1–2k msg/s on Postgres). Far above human chat rates. Mention it when asked about "a channel with 100k members all posting".
- An idempotent retry (same `clientMsgId`) must **not** bump `last_seq`. Check for the existing message first, or insert with `ON CONFLICT DO NOTHING` and roll back the bump when the insert is a no-op (do both in one transaction). The implementation task must include a test for this.
- A deleted message keeps its seq (the gap is intentional and visible as "message deleted").
- Migration: backfill `seq` for existing rows with `ROW_NUMBER() OVER (PARTITION BY channel_id ORDER BY id)`, then set `channel.last_seq`.

## Revisit when
- One channel needs > 1k msg/s sustained (e.g. livestream chat): switch that channel type to a Redis-assigned seq with periodic DB reconciliation, or shard the channel.
