# 04 — Soft delete, idempotent (notes)

Diagram: [../04-soft-delete.md](../04-soft-delete.md)

## Problem
A user deletes a message, the response is lost, and the client retries. Meanwhile a forum leader deletes the same message. Every caller should get the same answer, and the "deleted at" time shouldn't move.

## Without it
- **Hard delete:** replies point at a missing row (FK `reply_to_id` breaks or must cascade), the seq gap is unexplained, and moderation has no audit trail.
- **Unconditional `SET deleted_time = now()`:** every retry overwrites the timestamp, so the last writer rewrites history.
- **404 on the second delete:** the retry looks like a failure to the client.

## Mechanism
```sql
-- MessageRepository.markDeletedIfLive
UPDATE MessageEntity ms SET ms.deletedTime = :deletedTime
WHERE ms.id = :id AND ms.deletedTime IS NULL
```
- The lookup (`findDeletable`) includes deleted rows on purpose: a repeat delete must find the row to answer 204.
- Authorization (`MessageDeletePolicy`): the sender, a LOUNGE/FORUM `CREATOR`, or a FORUM `LEADER`. A private chat has no admin. A non-member gets 404, not 403 (no id probing).
- The service skips the UPDATE if the row it read is already deleted. In a race, the WHERE clause decides.
- Reads turn a deleted row into a **tombstone**: the history, the reply preview and the channel list's `lastMessage` all null the content but keep the id, seq and sender.

## DB concepts
- **Conditional UPDATE as first-writer-wins.** The same pattern as 03 and 08: put the precondition in the `WHERE` and read the row count. Under READ COMMITTED, a blocked writer re-checks the WHERE against the committed row, so the second delete matches 0 rows.
- **Idempotency at the HTTP level.** DELETE is idempotent by definition, and the DB pattern makes the *effect* idempotent too (one timestamp).
- **Soft delete vs hard delete.** Soft delete keeps referential integrity (replies, reactions, the seq order) and an audit trail. It costs storage and a `deleted_time IS NULL` filter on every query that must hide deleted rows.

## Trade-offs
- **Legal erase (GDPR):** soft delete isn't erasure. P6 plans asynchronous S3 media removal, and a real erase would also null `content` in the row.
- **Search and RAG** must filter `deleted_time IS NULL` too, or deleted text leaks through search (P3-T13, P8).
- A partial index `WHERE deleted_time IS NULL` would help if most rows were deleted. They aren't, so there's none.

## Interview Q&A
- **What does "deleted" mean in your system?** A row with `deleted_time` set: hidden content, kept position. Replies show "message deleted".
- **Why answer 204 to a second delete?** The client's goal ("this message is gone") is met. A 404 would make retries look like failures.
- **Why not `@SQLDelete` / `@Where` in Hibernate?** Some queries *need* deleted rows (repeat delete, tombstones, reply targets), so the filter is explicit per query.

## Blog angle
**"Soft delete done right: idempotent, race-safe, and honest about GDPR."**

## Code
- [`DeleteMessageService.delete`](../../../../src/main/java/com/devcool/application/service/chat/DeleteMessageService.java#L43)
- [`MessageRepository.markDeletedIfLive`](../../../../src/main/java/com/devcool/adapters/out/persistence/message/repository/MessageRepository.java#L129)
- [`MessageDeletePolicy`](../../../../src/main/java/com/devcool/domain/chat/policy/MessageDeletePolicy.java)
- Tests: [`DeleteMessageIT`](../../../../src/test/java/com/devcool/application/service/chat/DeleteMessageIT.java): `concurrentDeletes_allSucceed_oneTimestamp` (20 threads), `repeatDelete_succeeds_andKeepsTheFirstTimestamp`, plus the role matrix
