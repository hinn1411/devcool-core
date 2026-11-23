# 10 — Zero-downtime migrations (notes)

Diagram: [../10-migrations.md](../10-migrations.md) · Deep dive: [learning/11 Flyway and schema management](../../learning/11-flyway-and-schema-management.md) (§7 covers the migrate task and expand/contract)

## Problem
The schema must change under a running system: 2+ ECS tasks are serving traffic, and during a rolling deploy old and new code run at the same time against one database.

## Without it
- **`ddl-auto=update` on boot:** N tasks race to alter the schema, nothing is reviewed, and there's no backfill.
- **`ADD COLUMN seq NOT NULL` in one step:** old tasks' INSERTs fail for the whole rollout.
- **No lock timeout:** an `ALTER TABLE` waiting behind a long query queues **every** later query on that table, so a "fast" migration becomes an outage.

## Mechanism (what each migration teaches)
| Migration | Technique |
|---|---|
| `V1__baseline` | Generated from the entities once, reviewed by hand, then `ddl-auto=validate` everywhere. Pooled sequences `INCREMENT BY 50` to match Hibernate. |
| `V2` index | A composite index for the history query. Plain `CREATE INDEX` was fine because no DB was deployed yet. On a live table use `CREATE INDEX CONCURRENTLY` (outside a transaction). |
| `V3` INT→BIGINT | `ALTER COLUMN TYPE` **rewrites the table under ACCESS EXCLUSIVE**. Accepted on a small table with `lock_timeout`. Old code still reads bigint values below 2³¹, but the way back is fix-forward only. |
| `V4` expand | `ADD COLUMN … DEFAULT 0` is **metadata-only** since PG 11 (no rewrite). Backfill with `row_number() OVER (PARTITION BY channel_id ORDER BY id)`. Unique keys added **after** the backfill (each index built once). `seq` stays nullable. |
| `V5` contract | Number the rows inserted by old tasks *after* `last_seq`, **bumping `channel.last_seq` first, the same lock order as a live send**, then `SET NOT NULL`. |
| `V6`, `V7` | Nullable FK column, new table: no expand/contract needed. A documented decision *not* to add an index. |

Every file starts with `SET LOCAL lock_timeout = '5s'`. Flyway runs each migration in its own transaction (Postgres has transactional DDL), so `LOCAL` scopes it to that migration.

**Pipeline:** the app runs with Flyway **off** in `ecs`. `deploy.yml` runs a one-off ECS task (`ecs,migrate`) first, then rolls the service. A failed migration exits non-zero and nothing is rolled out.

## DB concepts
- **Transactional DDL.** A failed migration rolls back completely, with no half-applied schema.
- **Table lock levels.** `ACCESS EXCLUSIVE` (most `ALTER TABLE`s) conflicts with everything, even `SELECT`. `SHARE` (`CREATE INDEX`) blocks writes. `SHARE UPDATE EXCLUSIVE` (`CONCURRENTLY`, `VALIDATE CONSTRAINT`) blocks neither reads nor writes.
- **The lock queue.** Waiting lock requests queue in order, so a pending `ACCESS EXCLUSIVE` blocks new readers too. `lock_timeout` bounds that damage.
- **Advisory locks.** Flyway takes `pg_advisory_lock` so concurrent migrators serialize. That's an app-level mutex stored in Postgres.
- **NOT NULL on a big table.** `SET NOT NULL` scans the table under `ACCESS EXCLUSIVE`. The large-table route is `ADD CHECK (seq IS NOT NULL) NOT VALID` → `VALIDATE CONSTRAINT` (no write block) → `SET NOT NULL` (PG 12+ skips the scan thanks to the validated check) → drop the check (the V5 comment).
- **Lock order in data migrations.** V5 bumps the channel row first, exactly like a send. If it numbered rows first, a live send could take the same seq in between. The deadlock analysis is in [11](11-locks-and-isolation.md).

## Trade-offs
- V3 wasn't expand/contract (a new column, dual writes, then a swap would be). That was acceptable at dev scale and documented in the file.
- `validate` makes rollback fix-forward: an old image fails to start against a newer schema with fewer columns.

## Interview Q&A
- **Why is `ddl-auto` in production dangerous?** It's non-reviewed and non-repeatable, can't backfill, and N instances race.
- **What is expand/contract?** Add the new shape compatibly, deploy code that uses it, backfill, then remove or tighten the old shape in a later release.
- **What does `lock_timeout` protect you from?** The lock queue: a blocked DDL stalls every query behind it.
- **Why run migrations as a separate task?** One migrator, it fails before rollout, and app start-up stays fast.

## Blog angle
**"Adding a NOT NULL column to a live chat table: expand, deploy, contract."** Hook: the V4/V5 files, line by line.

## Code
- [`db/migration/`](../../../src/main/resources/db/migration/)
- Tests: [`V4BackfillIT`](../../../src/test/java/com/devcool/V4BackfillIT.java) and [`V5ContractIT`](../../../src/test/java/com/devcool/V5ContractIT.java) migrate a fresh database **to a target version** (`Flyway.configure().target(version)`), seed rows as the old code would, then apply the next version and assert. Also [`BigintIdsIT`](../../../src/test/java/com/devcool/BigintIdsIT.java) (ids above 2³¹ through REST, WS and history) and [`MigrateProfileIT`](../../../src/test/java/com/devcool/MigrateProfileIT.java).
