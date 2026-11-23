# 02 — Idempotent send (notes)

Diagram: [../02-idempotent-send.md](../02-idempotent-send.md) · Decision: [ADR-0012](../../plans/architecture/adr/0012-message-ordering-per-channel-seq.md) "Consequences"

## Problem
On mobile, a send times out but the server actually stored it. The client retries. Without idempotency the channel shows the message twice, and the retry burns a seq.

## Without it
- Duplicate messages on every flaky network.
- A retry that bumps `last_seq` makes other clients think they missed a message (a phantom gap).

## Mechanism
1. **Replay check first, before the bump** (`findReplay`, served by the unique index, a projection with no joins):
   ```sql
   SELECT ms.id, ms.channel.id, ms.seq, ms.createdTime
   FROM MessageEntity ms
   WHERE ms.user.id = :senderId AND ms.clientMsgId = :clientMsgId
   ```
2. **Insert with `saveAndFlush`.** The flush runs the INSERT now, so the unique violation surfaces inside `MessageAdapter.save`, where it can be recognized by constraint name and turned into `Optional.empty()`. Any other violation is rethrown.
3. **Lost the race → roll back, then re-read in a new transaction.** `MessageService.save` is deliberately *not* `@Transactional`. It uses `TransactionOperations` twice:
   - tx 1: `saveOnce`. On empty, `status.setRollbackOnly()` also undoes the seq bump.
   - tx 2: `findReplay` returns the winner's id and seq with `duplicate = true`.
4. **Key reused in another channel** → `ClientMsgIdReusedException` (a client bug, or a probe).

## DB concepts
- **Unique constraint as an idempotency key.** The check before the insert is only an optimization. The constraint is the guarantee.
- **Unique checks look past your snapshot.** T2's INSERT sees T1's *committed* row even though T2's snapshot is older. If T1 were still in flight, T2 would wait for T1's outcome.
- **Aborted transaction state (SQLSTATE 25P02).** After any error, Postgres rejects every statement until ROLLBACK, so the re-read *must* be a new transaction. (Savepoints are the alternative; see Trade-offs.)
- **Atomicity.** The bump, the insert and the read-marker update commit or roll back together, so a lost race leaves no trace.
- **Session state after a flush failure.** Hibernate's persistence context is unusable after a failed flush too, which is one more reason for a fresh transaction.

## Trade-offs
- **`INSERT … ON CONFLICT DO NOTHING RETURNING`** would avoid the exception, but the bump has already happened by then, so it still needs a rollback. Message inserts also go through JPA so they use Hibernate's pooled ids (ADR-0012).
- **Savepoint** (`ROLLBACK TO SAVEPOINT`) could keep one transaction, but it would keep the bump unless it also rolls back past it. Two short transactions are simpler.
- The key is scoped per **sender**, not global, so one user can't block another's UUID.

## Interview Q&A
- **Where does the idempotency key live and what does the second response contain?** In the `message` row, unique per sender. The retry gets the *original* id, seq and createdTime, plus `duplicate = true`.
- **Why check before the bump?** A retry must not move `last_seq`. The check-first path makes the common retry (sequential) cost one index lookup and no lock.
- **What if both retries arrive together?** Both miss the check. The row lock on the channel serializes them, the second hits the unique key, rolls back its bump and re-reads. Tested with 20 threads.

## Blog angle
**"Idempotent writes in Postgres: the race your retry logic forgot."** Hook: why `SELECT then INSERT` isn't enough, and why the recovery needs a second transaction.

## Code
- [`MessageService.save`](../../../src/main/java/com/devcool/application/service/chat/MessageService.java#L71)
- [`MessageAdapter.save`](../../../src/main/java/com/devcool/adapters/out/persistence/message/MessageAdapter.java#L49) (constraint-name match)
- [`MessageRepository.findKeyRow`](../../../src/main/java/com/devcool/adapters/out/persistence/message/repository/MessageRepository.java#L82)
- Tests: [`SendMessageSeqIT`](../../../src/test/java/com/devcool/application/service/chat/SendMessageSeqIT.java): `returnsSameSeqOnRetriedClientMsgId`, `concurrentRetriesOfOneKey_storeOneRow_andBumpOnce`, `keyReusedInAnotherChannel_isRejected_andNothingIsStoredThere`
