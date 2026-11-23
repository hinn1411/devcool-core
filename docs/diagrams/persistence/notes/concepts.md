# DB concepts review sheet

Each concept gets a one-line definition and the features in this folder that use it. Read this before an interview, then open the linked notes for the details.

## Transactions and isolation
| Concept | One line | Where |
|---|---|---|
| **ACID** | Atomicity, consistency, isolation, durability | mapped in [11](11-locks-and-isolation.md#acid-mapped) |
| **Atomicity / rollback** | All statements of a transaction commit together, or none do | seq bump undone on a lost race [02](02-idempotent-send.md), consume + issue [08](08-refresh-token.md), counter rollback [09](09-membership-races.md) |
| **READ COMMITTED** | Each statement sees data committed before it started. Postgres's default and DevCool's level | [11](11-locks-and-isolation.md) |
| **Re-check after a lock wait (EvalPlanQual)** | A blocked UPDATE re-evaluates its WHERE on the new committed row | [01](01-per-channel-seq.md), [03](03-optimistic-edit.md), [04](04-soft-delete.md), [06](06-read-state.md), [08](08-refresh-token.md) |
| **REPEATABLE READ / SERIALIZABLE** | Snapshot per transaction; SSI also detects write skew. Both need 40001 retries | [11](11-locks-and-isolation.md#what-the-higher-levels-would-change) |
| **Aborted transaction (25P02)** | After an error, Postgres rejects statements until ROLLBACK | [02](02-idempotent-send.md) |
| **MVCC** | Readers see a snapshot and never block writers; writers create new row versions | [11](11-locks-and-isolation.md) |
| **Transactional DDL** | Schema changes commit or roll back like data | [10](10-migrations.md) |

## Anomalies
| Anomaly | One line | Where it was (or could be) in DevCool |
|---|---|---|
| **Lost update** | Two read-modify-writes, one overwrites the other | naive `MAX(seq)+1` [01](01-per-channel-seq.md), last-write-wins edit [03](03-optimistic-edit.md), Java counter [09](09-membership-races.md) |
| **TOCTOU / check-then-act** | The checked state changes before the act | refresh token [08](08-refresh-token.md), duplicate member [09](09-membership-races.md) |
| **Write skew** | Two transactions read overlapping data and write *different* rows, breaking a rule together | reaction cap [05](05-reactions.md), lounge cap [09](09-membership-races.md) (both known gaps) |
| **Phantom** | A re-run query returns new rows | handled by unique indexes instead of "SELECT then INSERT" [02](02-idempotent-send.md), [05](05-reactions.md) |
| **Deadlock (40P01)** | A cycle of lock waits. Postgres aborts one transaction | V5 vs send analysis [11](11-locks-and-isolation.md) |

## Concurrency control techniques
| Technique | One line | Where |
|---|---|---|
| **Pessimistic row lock** | Lock the row, so the others queue | channel counter [01](01-per-channel-seq.md) |
| **Optimistic lock (version CAS)** | Write only if the version is unchanged, otherwise 409 | edit [03](03-optimistic-edit.md) |
| **Conditional UPDATE + row count** | Precondition in the WHERE, and the affected rows are the answer | delete [04](04-soft-delete.md), token [08](08-refresh-token.md), read marker [06](06-read-state.md) |
| **Atomic increment** | `x = x + :d` in SQL, never in Java | `last_seq` [01](01-per-channel-seq.md), `total_of_members` [09](09-membership-races.md) |
| **Monotonic update** | `GREATEST(old, new)`: order-independent | read marker [06](06-read-state.md) |
| **Unique constraint as concurrency control** | The index check sees in-flight inserts | idempotent send [02](02-idempotent-send.md), members [09](09-membership-races.md) |
| **Upsert (`ON CONFLICT DO NOTHING`)** | Insert or skip atomically | reactions [05](05-reactions.md) |
| **Idempotency key** | The client supplies the identity of the attempt | `client_msg_id` [02](02-idempotent-send.md) |
| **Consistent lock ordering** | Every path locks in the same order, so no cycles | channel → message → member [11](11-locks-and-isolation.md) |
| **Advisory lock** | An app-defined mutex held in Postgres | Flyway migrator [10](10-migrations.md) |
| **`lock_timeout`** | Give up on a lock instead of queuing forever | every migration [10](10-migrations.md) |

## Locks
| Concept | One line | Where |
|---|---|---|
| **Row lock modes** | KEY SHARE / SHARE / NO KEY UPDATE / UPDATE | compatibility table [11](../11-locks-and-isolation.md) |
| **FK checks take KEY SHARE** | So non-key updates of the parent don't block child inserts | send, reply, reaction [11](11-locks-and-isolation.md) |
| **Table lock levels** | ACCESS SHARE … ACCESS EXCLUSIVE | DDL in [10](10-migrations.md) |
| **Lock queue** | A waiting ACCESS EXCLUSIVE blocks the readers behind it | [10](10-migrations.md) |

## Indexing and queries
| Concept | One line | Where |
|---|---|---|
| **Composite B-tree, leftmost prefix** | `(a, b)` serves `a = ?` + a range or order on `b` | `(channel_id, seq)` [07](07-keyset-history.md) |
| **Backward index scan** | ASC index serves ORDER BY DESC | history [07](07-keyset-history.md) |
| **One index, many jobs** | A unique constraint doubles as a query index | [07](07-keyset-history.md), [05](05-reactions.md) |
| **Index write cost** | Every index slows inserts, so reuse before adding | no extra seq index [07](07-keyset-history.md) |
| **Keyset (seek) pagination** | `WHERE key below :cursor ORDER BY key LIMIT n` | history [07](07-keyset-history.md) |
| **N+1** | 1 query + 1 per row, fixed with JOIN FETCH or batching | history, reactions, channel list [05](05-reactions.md), [06](06-read-state.md), [07](07-keyset-history.md) |
| **Projections** | Select only the needed columns into a DTO | key and edit lookups [07](07-keyset-history.md) |
| **Aggregation in SQL** | `GROUP BY` + `count` + `bool_or` in one pass | reaction counts [05](05-reactions.md) |
| **Window functions** | `row_number() OVER (PARTITION BY …)` | backfill [10](10-migrations.md) |
| **`UPDATE … RETURNING` / `UPDATE … FROM`** | Read the result in the same statement / join in an update | [01](01-per-channel-seq.md), [06](06-read-state.md) |

## Schema design
| Concept | One line | Where |
|---|---|---|
| **Constraints as the last line of defence** | PK, FK, UNIQUE, CHECK | [00](00-schema.md) |
| **Natural vs surrogate keys** | `message_reaction` PK vs `message.id` + `(channel_id, seq)` | [00](00-schema.md) |
| **Denormalized counters** | Store derived values, update them transactionally | `last_seq`, `last_read_seq`, `total_of_members` [06](06-read-state.md) |
| **Soft delete / tombstones** | Hide, don't remove | [04](04-soft-delete.md) |
| **Sequences, pooled ids and gaps** | Ids are non-transactional and gappy; seq is gapless | [01](01-per-channel-seq.md) |
| **Expand / contract** | Change the schema compatibly across a rolling deploy | [10](10-migrations.md) |
| **Metadata-only ALTER vs table rewrite** | `ADD COLUMN DEFAULT const` vs `ALTER TYPE` | [10](10-migrations.md) |

## Testing
| Concept | One line | Where |
|---|---|---|
| **Real-engine ITs** | Testcontainers, not H2 | [12](12-concurrency-tests.md) |
| **Latch-released race tests** | 20 threads, one `countDown()` | [12](12-concurrency-tests.md) |
| **Statement counting** | Hibernate `Statistics` asserts no N+1 | [12](12-concurrency-tests.md) |
| **Version-targeted migration tests** | Migrate to Vn, seed old-shape data, migrate on | [10](10-migrations.md) |
