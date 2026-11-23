# 07 — Keyset history (notes)

Diagram: [../07-keyset-history.md](../07-keyset-history.md)

## Problem
#general has 1M messages across all channels and 50k of them in #general. Opening it must read the newest 20, scrolling up must read the 20 before, and a reconnect must read exactly what was missed. Every page should cost the same, however deep you scroll, while new messages keep arriving.

## Without it
- **OFFSET:** page *k* reads and throws away `k × 20` rows (slower the deeper you go), and an arriving message shifts every page, giving duplicates or skips (first diagram).
- **No composite index:** filter `channel_id = 7`, then sort every match, for every page.
- **Cursor on `id`:** V5 numbered the NULL-seq rows (from the rollout) *after* `last_seq`, so id order ≠ seq order for those rows. An id cursor would skip or repeat compared to the push.

## Mechanism
```sql
-- MessageRepository.findPageBeforeSeq (JPQL)
WHERE ms.channel.id = :channelId
  AND (:beforeSeq IS NULL OR ms.seq < :beforeSeq)
ORDER BY ms.seq DESC          -- + Pageable LIMIT

-- findPageAfterSeq
WHERE ms.channel.id = :channelId AND ms.seq > :afterSeq
ORDER BY ms.seq ASC
```
- **Index history:** V2 added `(channel_id, id DESC)` in P1 for the old id cursor. V4's `UNIQUE(channel_id, seq)` now serves both seq directions. A B-tree can be scanned backwards, so no separate `DESC` index is needed. The V2 index stays only while the deprecated `cursorId` alias lives.
- **The next cursor is read off the last row**, never computed (`beforeSeq − limit`): bounds are exclusive, and a future hard erase (P6) would leave gaps.
- **No N+1:** each history query `JOIN FETCH`es sender, media, reply parent, the parent's sender and the parent's media in one SQL statement. They're all to-one associations, so `LIMIT` stays in SQL (a to-many fetch would make Hibernate paginate in memory).
- **Projections** for hot point reads: `findKeyRow` (02) and `findEditableRow` (03) select only the needed columns into records, with no entity, no join and no dirty checking.

## DB concepts
- **Composite B-tree, leftmost prefix.** `(channel_id, seq)` answers `channel_id = ?` + a range on `seq` + ordering by `seq`: an index range scan that stops after `LIMIT` rows.
- **Keyset (seek) pagination.** The cost is O(log n + page) at any depth, and it's stable under inserts.
- **One index, three jobs:** the uniqueness of the position, history in both directions, and the channel-list preview point lookup (`seq = last_seq`, 06).
- **Write cost of indexes.** Every index slows inserts. Reusing the unique index instead of adding a `(channel_id, seq DESC)` saves one index write per message.

## Trade-offs
- `(:beforeSeq IS NULL OR …)` is fine with Postgres custom plans. If a cached generic plan ever ignores the index, split it into two queries. Check with `EXPLAIN (ANALYZE)` under the JDBC driver's prepared statements.
- Keyset can't jump to "page 37". Chat never needs that.

## Interview Q&A
- **Why is OFFSET bad for feeds?** It costs O(offset) and isn't stable under concurrent inserts.
- **Does `ORDER BY seq DESC` need a DESC index?** No. Postgres scans an ascending B-tree backwards. You only need mixed directions (`a ASC, b DESC`) in the index definition.
- **How do you prove there's no N+1?** Hibernate `Statistics.getPrepareStatementCount()` asserted in an IT: one statement per page.

## Blog angle
**"One index, three queries: keyset pagination for a chat history."**

## Code
- [`MessageRepository.findPageBeforeSeq`](../../../../src/main/java/com/devcool/adapters/out/persistence/message/repository/MessageRepository.java#L50), [`findPageAfterSeq`](../../../../src/main/java/com/devcool/adapters/out/persistence/message/repository/MessageRepository.java#L67)
- Migrations: [`V2`](../../../../src/main/resources/db/migration/V2__message_channel_id_index.sql), [`V4`](../../../../src/main/resources/db/migration/V4__message_seq_and_read_state.sql)
- Tests: [`MessageAdapterIT`](../../../../src/test/java/com/devcool/adapters/out/persistence/message/MessageAdapterIT.java): `seqPaging_followsSeqEvenWhenIdOrderDiffers`, `beforeSeq_returnsOlderBySeqDescending_excludingTheBound`, `afterSeq_returnsNewerBySeqAscending_excludingTheBound`, `pageOfReplies_isOneSqlStatement`
