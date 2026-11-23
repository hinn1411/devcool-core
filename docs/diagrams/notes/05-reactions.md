# 05 — Reactions (notes)

Diagram: [../05-reactions.md](../05-reactions.md)

## Problem
Users double-tap 👍 on a laggy phone. The second tap must not create a second row or an error. Each history page also needs "👍 3 (you)" for every message without one query per message.

## Without it
- `SELECT exists → INSERT`: two taps both see "absent" and one fails with a unique violation (500), or with no key you get a duplicate row.
- A lazy `@OneToMany reactions` on the message: 1 query for the page + 1 per message (**N+1**).

## Mechanism
**Add (idempotent):**
```sql
-- MessageReactionRepository.insertIfAbsent
insert into message_reaction (message_id, user_id, emoji, created_time)
values (:messageId, :userId, :emoji, :createdTime)
on conflict (message_id, user_id, emoji) do nothing
```
**Remove (idempotent):** `delete … where message_id = ? and user_id = ? and emoji = ?`. 0 rows is fine, and you can only delete your own reaction.

**Counts for a page, one statement:**
```sql
-- MessageReactionRepository.countByMessage
select message_id, emoji, count(*) as total, bool_or(user_id = :viewerId) as mine
from message_reaction
where message_id in (:messageIds)
group by message_id, emoji
order by message_id, min(created_time), emoji
```
An empty page runs no statement at all.

## DB concepts
- **Composite natural PK as an idempotency key.** No surrogate id: the key *is* the business rule (one 👍 per user per message).
- **Upsert (`ON CONFLICT`).** Atomic insert-or-skip. A concurrent same-key insert waits for the in-flight one to commit or roll back, then skips or inserts. No error, no retry loop. (Hibernate has no portable upsert, hence native SQL.)
- **Index reuse.** The PK's leading column is `message_id`, so `where message_id in (…)` uses it, and no extra index is needed (V7 comment).
- **Aggregation in the DB.** `count(*)` plus `bool_or(…)` returns totals *and* "did I react" in one pass. `min(created_time)` orders emojis by first use.
- **Write skew (third diagram).** Two transactions read the same set (19 emojis), each makes a decision that's valid alone, and they write *different* rows. Row locks can't help because there's no common row. Only SERIALIZABLE (SSI) or an explicit lock on a shared row detects it.

## Trade-offs
- **The cap overshoot is accepted** (the comment in `ReactionService.react`): the limit is cosmetic, and the fix costs a lock on every reaction. Fixes if it ever matters:
  1. `SELECT … FROM message WHERE id = ? FOR UPDATE` before counting (serializes reactions per message).
  2. A `distinct_emoji_count` column updated atomically with a `CHECK (≤ 20)`.
  3. SERIALIZABLE for this one transaction, plus a retry on SQLSTATE 40001.
- `emoji varchar(16)` counts code points, which matches `Emoji.MAX_CODE_POINTS`. A ZWJ family emoji is several code points.

## Interview Q&A
- **How do you avoid N+1?** Batch by ids: one grouped query per page, merged in memory. Tests assert the statement count.
- **Give a real example of write skew.** The 20-emoji cap, explained above, plus how you would fix it and why I didn't.
- **Why not `INSERT … ON CONFLICT DO UPDATE`?** There's nothing to update. `DO NOTHING` keeps the original `created_time` (first use).

## Blog angle
**"The race condition I chose to keep."** Hook: write skew explained with emoji reactions, and three ways to fix it, priced.

## Code
- [`ReactionService.react`](../../../src/main/java/com/devcool/application/service/chat/ReactionService.java#L48)
- [`MessageReactionRepository`](../../../src/main/java/com/devcool/adapters/out/persistence/reaction/repository/MessageReactionRepository.java)
- Migration: [`V7__message_reaction.sql`](../../../src/main/resources/db/migration/V7__message_reaction.sql)
- Tests: [`ReactionAdapterIT`](../../../src/test/java/com/devcool/adapters/out/persistence/reaction/ReactionAdapterIT.java) (`addTwice_keepsOneRow`, `summarize_aWholePage_isOneStatement`, `summarize_emptyPage_runsNoStatement`), [`MessageReactionIT.concurrentReacts_sameUserAndEmoji_leaveOneRow`](../../../src/test/java/com/devcool/adapters/in/web/controller/MessageReactionIT.java#L118) (20 threads)
