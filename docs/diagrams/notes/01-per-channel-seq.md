# 01 — Per-channel message sequence (notes)

Diagram: [../01-per-channel-seq.md](../01-per-channel-seq.md) · Decision: [ADR-0012](../../plans/architecture/adr/0012-message-ordering-per-channel-seq.md)

## Problem
A client that reconnects must ask "give me everything after message N in #general" and see gaps it missed. That needs a number that is **per channel, gapless and in commit order**.

## Without it
- `created_time`: clock skew between ECS tasks and ties. You can't tell "missing" from "never sent".
- Global `message.id`: ids skip across channels and across Hibernate's 50-id blocks, so a gap means nothing.
- `MAX(seq) + 1`: lost update (second diagram). Two senders read 41 and both write 42.

## Mechanism
The counter lives on the channel row and is bumped **inside the send transaction**:

```sql
-- ChannelRepository.bumpLastSeq
UPDATE channel SET last_seq = last_seq + 1, last_message_at = now()
WHERE id = :channelId
RETURNING last_seq
```

Inside one transaction, `MessageService.saveOnce` runs: replay check → reply-target check → **bump** → insert → advance the sender's read marker. The lock is held until commit, so the next sender in that channel waits for it.

`message.id` stays the global reference (replies, citations). It comes from a pooled sequence (`INCREMENT BY 50`), so it has gaps, and that's fine. `seq` is the user-visible order.

## DB concepts
- **Row-level lock from UPDATE.** A non-key `UPDATE` takes `FOR NO KEY UPDATE` on the row. A second writer to the *same* row waits, and other rows are untouched.
- **`UPDATE … RETURNING`.** Read-modify-write in one statement and one round-trip, with no `SELECT … FOR UPDATE` before it.
- **READ COMMITTED re-check.** When T2 wakes up, Postgres re-evaluates its UPDATE against the newest committed row version (42), not T2's snapshot. That's why T2 gets 43 and not 42.
- **Lock compatibility with foreign keys.** The `INSERT message` checks the FK by taking `FOR KEY SHARE` on the channel row. That mode is compatible with `FOR NO KEY UPDATE`, so the insert doesn't block on its own transaction's lock, or on other senders' FK checks.
- **Rollback undoes the bump.** If the insert fails, the `UPDATE` rolls back with it, so a failed send leaves no gap. Deleted messages keep their seq, and that gap is intentional ("message deleted").
- **Unique constraint as a backstop.** `UNIQUE(channel_id, seq)` makes any bug a loud error instead of a silent duplicate.

## Trade-offs
- **Hot-channel ceiling:** one commit at a time per channel, roughly 1–2k msg/s on Postgres. Fine for human chat. A livestream channel would need a Redis-assigned seq or sharding (ADR-0012 "Revisit when").
- Keep the transaction short: the lock is held through the insert and the marker update. No network calls (S3, Bedrock) inside it.
- Redis `INCR` was rejected: a second source of truth, and a failover can reuse numbers.

## Interview Q&A
- **Why not a Postgres sequence per channel?** Sequences are non-transactional: a rolled-back `nextval` leaves a gap, and one sequence per channel doesn't scale as DDL.
- **What happens under 100 concurrent senders in one channel?** They queue on one row lock. Throughput is bounded by commit latency. Senders in other channels are unaffected.
- **Why not SERIALIZABLE?** It would detect the conflict and abort one sender, which the app then has to retry. A row lock *queues* the senders instead. Same correctness, no retries.
- **Is the order "real time"?** It's commit order, which is what every reader sees. That's the only order that matters to clients.

## Blog angle
**"Ordering chat messages with one UPDATE statement."** Hook: a gapless per-channel sequence without Redis, Snowflake or a distributed lock, and what it costs.

## Code
- [`ChannelRepository.bumpLastSeq`](../../../src/main/java/com/devcool/adapters/out/persistence/channel/repository/ChannelRepository.java#L97)
- [`MessageService.saveOnce`](../../../src/main/java/com/devcool/application/service/chat/MessageService.java#L103)
- Migration: [`V4__message_seq_and_read_state.sql`](../../../src/main/resources/db/migration/V4__message_seq_and_read_state.sql)
- Test: [`SendMessageSeqIT.parallelSends_getGaplessSeqs`](../../../src/test/java/com/devcool/application/service/chat/SendMessageSeqIT.java#L85) (20 threads → seqs 1..20)
