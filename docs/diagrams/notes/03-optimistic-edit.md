# 03 — Edit with optimistic locking (notes)

Diagram: [../03-optimistic-edit.md](../03-optimistic-edit.md)

## Problem
Alice edits a message in two tabs. Both started from version 3. Without a guard, the second save silently overwrites the first: a **lost update**.

## Without it
- Last write wins. Tab A's edit disappears and nobody is told.
- With a pessimistic lock (`SELECT … FOR UPDATE` when the edit box opens), a row stays locked while a human types, so a forgotten tab blocks deletes.

## Mechanism
The client sends the version it edited from. The write is a **compare-and-set**:

```sql
-- MessageRepository.updateContentIfVersion
UPDATE MessageEntity ms
SET ms.content = :content, ms.version = ms.version + 1, ms.editedTime = :editedTime
WHERE ms.id = :id AND ms.version = :expected AND ms.deletedTime IS NULL
```

`EditMessageService.edit` checks in order: row exists and is live → caller is a member (otherwise 404, no id probing) → caller is the sender (403) → TEXT/MARKDOWN (400) → within 24 h (403) → version matches (409 early) → same content (200, no write) → CAS. **0 rows → 409.**

## DB concepts
- **Optimistic concurrency control.** No lock while thinking. Detect the conflict at write time with a version column, and the client decides (reload, merge, retry).
- **Why it's race-free under READ COMMITTED.** T2's UPDATE blocks on T1's row lock. After T1 commits, Postgres **re-evaluates T2's WHERE against the new row version** (internally "EvalPlanQual"). `version = 3` is now false → 0 rows. No SERIALIZABLE needed.
- **Why not JPA `@Version`.** `@Version` checks the version Hibernate *loaded*, so a stale browser tab would pass. Here the expected version comes from the **client**, so the check spans the whole read-think-write cycle, not one request.
- **`deleted_time IS NULL` in the same WHERE.** A delete that commits between the read and the write also turns the edit into 0 rows (409) instead of resurrecting content.
- **`@Modifying(flushAutomatically, clearAutomatically)`.** A bulk JPQL UPDATE bypasses the persistence context, so flush pending changes first and clear stale entities after.

## Trade-offs
- The early version check (before the UPDATE) is only a fast path. The CAS is the real guard.
- Optimistic locking fits low contention (one person editing their own message). Under high contention it turns into retry storms, and a queue or lock is better there. Compare with 01, where every sender contends on one row, so a lock is the right tool.

## Interview Q&A
- **Optimistic vs pessimistic: when do you pick which?** Optimistic when conflicts are rare and a human is in the loop. Pessimistic when conflicts are frequent and the critical section is short (01).
- **What does the losing client get?** 409 `MessageVersionConflict`. It refetches (the history includes `version`) and re-applies the edit.
- **Isn't there a race between the SELECT and the UPDATE?** Yes, which is why the UPDATE re-checks the version itself. The SELECT only drives the friendly errors.

## Blog angle
**"Lost updates and the one-line fix: `WHERE version = :expected`."** Hook: why Hibernate's `@Version` didn't fit, and the READ COMMITTED re-check that makes the plain version safe.

## Code
- [`EditMessageService.edit`](../../../src/main/java/com/devcool/application/service/chat/EditMessageService.java#L41)
- [`MessageRepository.updateContentIfVersion`](../../../src/main/java/com/devcool/adapters/out/persistence/message/repository/MessageRepository.java#L139)
- Tests: [`EditMessageConcurrencyIT`](../../../src/test/java/com/devcool/application/service/chat/EditMessageConcurrencyIT.java): `concurrentEditsOnOneVersion_exactlyOneWins` (20 threads), `staleClient_getsConflict_andTheNewerEditSurvives`. [`MessageAdapterIT`](../../../src/test/java/com/devcool/adapters/out/persistence/message/MessageAdapterIT.java): `updateContent_staleVersion_leavesRowUnchanged`, `updateContent_onDeletedMessage_matchesNothing`
