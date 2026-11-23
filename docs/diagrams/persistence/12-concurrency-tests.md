# 12 — Testing concurrency on a real Postgres

Every race in this folder has a test that **actually races**: 20 threads released by one latch against the same pgvector Postgres 16 image that production runs, plus statement-count assertions for N+1 and version-targeted migration tests.

## Race test anatomy

```mermaid
flowchart TB
    C["Testcontainers: pgvector/pgvector:pg16, pinned digest, one container per JVM"] --> SEED[Seed: channel, members, message]
    SEED --> POOL["ExecutorService with 20 threads"]
    POOL --> W1[thread 1: start.await]
    POOL --> W2[thread 2: start.await]
    POOL --> WN[thread 20: start.await]
    GO["start.countDown(): release all at once"] --> W1 & W2 & WN
    W1 & W2 & WN --> CALL["call the real service (own transactions)"]
    CALL --> F["futures.get(30 s)"]
    F --> A["Assert the invariant in the DB: seqs 1..20, one row, one winner, max marker"]
```

| Test | Races | Invariant asserted |
|---|---|---|
| `SendMessageSeqIT.parallelSends_getGaplessSeqs` | 20 sends, one channel | seqs are exactly 1..20, `last_seq` = 20 |
| `SendMessageSeqIT.concurrentRetriesOfOneKey_storeOneRow_andBumpOnce` | 20 sends, one `clientMsgId` | 1 row, all callers get seq 1, one non-duplicate, `last_seq` = 1 |
| `EditMessageConcurrencyIT.concurrentEditsOnOneVersion_exactlyOneWins` | 20 edits on version 0 | exactly 1 wins, the other 19 throw `MessageVersionConflictException` (409) |
| `DeleteMessageIT.concurrentDeletes_allSucceed_oneTimestamp` | 20 deletes by the sender | no call fails, `deleted_time` set (first-timestamp-kept is `repeatDelete_…`) |
| `MessageReactionIT.concurrentReacts_sameUserAndEmoji_leaveOneRow` | 20 identical reacts | one row |
| `ChannelReadIT.concurrentReads_endAtTheHighestSeq` | 20 markers, seqs 1..20 | `last_read_seq` = 20 whatever the commit order |

**Read:** [notes/12-concurrency-tests.md](notes/12-concurrency-tests.md)
