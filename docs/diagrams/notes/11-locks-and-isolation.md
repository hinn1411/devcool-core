# 11 — Locks, deadlocks and isolation (notes)

Diagram: [../11-locks-and-isolation.md](../11-locks-and-isolation.md)

This is an **analysis** of the code, not a feature with its own test. The interview value is being able to say *why* the system is correct at READ COMMITTED, and where it isn't.

## Problem
Interviewers ask: "What isolation level do you use? Can it deadlock? What anomalies can happen?" A good answer names the level, the mechanism that makes each write safe, and the known holes.

## Isolation: what we use and why it's enough
No code overrides isolation, so every transaction runs at Postgres's default **READ COMMITTED**: each *statement* sees a fresh snapshot of committed data.

| Anomaly | Possible at READ COMMITTED? | How DevCool handles it |
|---|---|---|
| Dirty read | No (Postgres never allows it) | — |
| Non-repeatable read | Yes | Decisions that matter are re-checked *inside* the writing statement (`WHERE version = ?`, `WHERE deleted_time IS NULL`, `WHERE consumed_time IS NULL`) |
| Lost update | Yes, for read-modify-write in Java | Increments are done in SQL (`last_seq + 1`, `total + :delta`, `GREATEST(…)`) |
| Phantom | Yes | Uniqueness is enforced by unique indexes, not by "SELECT then INSERT" |
| Write skew | Yes | **Two known cases:** the reaction cap (05) and the lounge cap (09). Both are check-then-act on data no row lock covers |

**The key mechanism:** when an UPDATE or DELETE waits on a row lock, Postgres re-evaluates its `WHERE` against the *newly committed* row version before applying it (EvalPlanQual). That turns every conditional UPDATE in this codebase into a safe compare-and-set without raising the isolation level.

### What the higher levels would change
- **REPEATABLE READ** (snapshot isolation): the blocked UPDATE wouldn't re-check. It would fail with **40001 serialization failure**, so every CAS path would need a retry loop to keep its current "0 rows → 409" behaviour.
- **SERIALIZABLE** (SSI): also catches write skew (the two caps), at the cost of 40001 retries for any transaction in a dangerous read/write pattern. Worth it only for the transactions that need it.

## Locks
- **Row locks** come from UPDATE/DELETE (`FOR NO KEY UPDATE` / `FOR UPDATE`) and from FK checks (`FOR KEY SHARE`). They're held until commit.
- **Table locks** come from every statement (`ACCESS SHARE` for SELECT, `ROW EXCLUSIVE` for writes) and from DDL (`ACCESS EXCLUSIVE`). They're held until commit too, which is what makes the V5 scenario possible.
- **The serialization point** is the channel row: sends and member adds in a channel queue on it, and the rest of the system doesn't touch it.
- **The FK lock split** (`KEY SHARE` vs `NO KEY UPDATE`, PG 9.3+) is why a send's channel bump doesn't block other senders' FK checks, and why soft-deleting a message doesn't block replies or reactions to it.

## Deadlocks
- **Definition:** a cycle of waits, where T1 holds A and wants B while T2 holds B and wants A. Postgres checks for cycles after `deadlock_timeout` (1 s), aborts one transaction with **40P01**, and the other proceeds.
- **App paths can't form a cycle:** every path locks the channel before member rows, touches at most one existing member row, and the member rows `addMember` updates are rows it just inserted (invisible to others).
- **Migration vs send can** (third diagram): V5 holds channel row locks and then asks for `ACCESS EXCLUSIVE` on `message`, while a send holds `ACCESS SHARE` on `message` (from its replay SELECT) and asks for the channel row. It only happens if a send hits a channel V5 is renumbering at that instant. **Both outcomes are safe:** a send aborted with 40P01 is retried idempotently (02), and an aborted migration stops the deploy (10). `lock_timeout = 5s` bounds plain waits.
- **Future risk (P3-T11):** "transfer leader" updates two member rows. Lock them in a fixed order (e.g. by id, or `SELECT … ORDER BY id FOR UPDATE`) or two opposite transfers can deadlock.

## ACID, mapped
| Property | Where it shows |
|---|---|
| **Atomicity** | Bump + insert + read marker commit together, and a lost key race rolls the bump back (01, 02). Consume + issue new token (08). |
| **Consistency** | UNIQUE/CHECK/FK constraints, plus `ddl-auto=validate` keeping code and schema aligned (00). |
| **Isolation** | READ COMMITTED + atomic conditional statements + row lock serialization (this file). |
| **Durability** | A commit is WAL-flushed before it returns. Aurora replicates storage across 3 AZs. |

## Interview Q&A
- **Which isolation level, and why not SERIALIZABLE?** READ COMMITTED. Each invariant is enforced by an atomic statement, a row lock or a constraint. SERIALIZABLE would add retries everywhere to fix two cosmetic caps.
- **Can your system deadlock?** Not between app paths (consistent lock order). It can between a contract migration and a send, and both outcomes are safe. Explain the cycle.
- **What happens to a blocked conditional UPDATE?** It waits, then re-checks its WHERE on the new row version: the 0-rows path.
- **How would you retry on 40P01/40001?** Retry the *whole* transaction with backoff, and only if it's idempotent. Send already is (clientMsgId).

## Blog angle
**"Why I didn't need SERIALIZABLE: correctness at READ COMMITTED, and the two places it isn't enough."**

## Code
- Every conditional statement: [`MessageRepository`](../../../src/main/java/com/devcool/adapters/out/persistence/message/repository/MessageRepository.java), [`ChannelRepository`](../../../src/main/java/com/devcool/adapters/out/persistence/channel/repository/ChannelRepository.java), [`MemberRepository`](../../../src/main/java/com/devcool/adapters/out/persistence/member/repository/MemberRepository.java), [`RefreshTokenRepository`](../../../src/main/java/com/devcool/adapters/out/persistence/auth/repository/RefreshTokenRepository.java)
- Lock order: [`MessageService.saveOnce`](../../../src/main/java/com/devcool/application/service/chat/MessageService.java#L103), [`V5__message_seq_not_null.sql`](../../../src/main/resources/db/migration/V5__message_seq_not_null.sql)
- Postgres docs: [Transaction isolation](https://www.postgresql.org/docs/16/transaction-iso.html), [Explicit locking](https://www.postgresql.org/docs/16/explicit-locking.html)
