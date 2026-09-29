# 05 — PostgreSQL, Aurora and data access

> **Used in DevCool:** [ADR-0009](../plans/architecture/adr/0009-aurora-postgres-pgvector.md) (Aurora Serverless v2) · [ADR-0012](../plans/architecture/adr/0012-message-ordering-per-channel-seq.md) (seq via `UPDATE … RETURNING`) · [03 §2–§3, §9](../plans/architecture/03-chat-system-design.md#2-data-model) · [Phase 1](../plans/phases/phase-1-foundation-hardening.md) (Flyway, P1-T01–T03) · [Phase 3](../plans/phases/phase-3-core-chat.md) (schema, history, FTS) · P6-T01 (outbox table)
> **Already in the repo:** [learning/01 — JPA and transactions](../learning/01-jpa-and-transactions.md) · [improvements: list-messages](../improvements/2026-09-19-list-messages-improvements.md) · [.claude/rules/flyway.md](../../.claude/rules/flyway.md)
> **Links checked:** 2026-09-29 · Versions assumed: PostgreSQL 16, Hibernate 6.6 (Spring Boot 3.5)

## Concepts to own

- **Row locks serialize writers.** `UPDATE channel SET last_seq = last_seq + 1 … RETURNING last_seq` takes a row lock held until commit, so writers to one channel queue up and different channels don't contend. *In DevCool:* per-channel seq (ADR-0012).
- **`FOR UPDATE SKIP LOCKED`.** Lock the rows you can get and skip the rest, so several relays or workers split a queue table without double-processing. *In DevCool:* outbox relay (P6-T05).
- **`INSERT … ON CONFLICT DO NOTHING`.** An idempotent insert against a unique constraint; combined with a unique key it gives dedupe. *In DevCool:* `(sender_id, client_msg_id)`, `processed_event`.
- **MVCC and isolation levels.** Postgres default is Read Committed. Know what that allows (non-repeatable reads, lost updates without locks or versions) and when you need `SELECT … FOR UPDATE` or Serializable.
- **Optimistic concurrency.** A `version` column checked on update (`@Version` in JPA) turns a lost update into a conflict (409). *In DevCool:* message edits, and the version guard for async consumers.
- **Keyset (seek) pagination.** `WHERE seq < :before ORDER BY seq DESC LIMIT n` on an index is O(page), where `OFFSET` is O(offset). *In DevCool:* history and `RESUME`.
- **Full-text search.** `tsvector` generated column + GIN index + `websearch_to_tsquery`. *In DevCool:* P3-T13.
- **Migrations as code.** Flyway versioned scripts, a baseline for an existing schema, and `ddl-auto=validate` so Hibernate never changes the schema. Never edit a merged migration.
- **JPA pitfalls.** N+1 from lazy associations (MapStruct walks the graph), transaction boundaries (`@Transactional` on public methods called through the proxy), and when to drop to `JdbcTemplate` for SQL JPA can't express well.
- **Connection budgets.** `Hikari max pool × tasks ≤ max_connections`. A small pool is usually faster than a big one.
- **Aurora Serverless v2.** Capacity in ACUs, scale to 0 with auto-pause (seconds to resume), one writer + optional readers, RDS-managed master secret. RDS Proxy when many tasks open connections.

## Read first

1. [PostgreSQL — SELECT, the locking clause](https://www.postgresql.org/docs/16/sql-select.html#SQL-FOR-UPDATE-SHARE) — *official docs* · `FOR UPDATE`, `NOWAIT`, `SKIP LOCKED` and their caveats (inconsistent view of the data, meant for queue-like tables).
2. [PostgreSQL — Transaction isolation](https://www.postgresql.org/docs/16/transaction-iso.html) — *official docs* · What Read Committed does on concurrent `UPDATE` (re-check of the row), which is why the seq bump is safe.
3. [We need tool support for keyset pagination / "No Offset"](https://use-the-index-luke.com/no-offset) — *Markus Winand, Use The Index, Luke* · Why `OFFSET` gets slower per page and how seek pagination fixes it.
4. [N+1 query problem with JPA and Hibernate](https://vladmihalcea.com/n-plus-1-query-problem/) — *Vlad Mihalcea* · The N+1 detection and fixes (JOIN FETCH, entity graphs, DTO projections) behind learning/01.
5. [Aurora Serverless v2](https://docs.aws.amazon.com/AmazonRDS/latest/AuroraUserGuide/aurora-serverless-v2.html) — *AWS docs* · ACUs, scaling, and the auto-pause page linked from it.

## Reference

### PostgreSQL SQL features used by the plan

- [INSERT — ON CONFLICT clause](https://www.postgresql.org/docs/16/sql-insert.html#SQL-ON-CONFLICT) — *official docs* · `DO NOTHING` vs `DO UPDATE`, and conflict targets.
- [UPDATE (with RETURNING)](https://www.postgresql.org/docs/16/sql-update.html) — *official docs* · Getting the new `last_seq` back in one round trip.
- [Explicit locking](https://www.postgresql.org/docs/16/explicit-locking.html) — *official docs* · Row-level lock modes and deadlocks.
- [Full text search](https://www.postgresql.org/docs/16/textsearch.html) — *official docs* · Start with "Tables and Indexes" and "Controlling Text Search" (ranking, `websearch_to_tsquery`).
- [GIN indexes](https://www.postgresql.org/docs/16/gin.html) — *official docs* · Used by FTS and by the `message_ids BIGINT[]` index in the chunk table.
- [Generated columns](https://www.postgresql.org/docs/16/ddl-generated-columns.html) — *official docs* · Stored generated `tsvector`.
- [Partial indexes](https://www.postgresql.org/docs/16/indexes-partial.html) — *official docs* · The `WHERE published_at IS NULL` index on the outbox.
- [Window functions tutorial](https://www.postgresql.org/docs/16/tutorial-window.html) — *official docs* · `ROW_NUMBER() OVER (PARTITION BY channel_id ORDER BY id)` for the seq backfill.
- [Table partitioning](https://www.postgresql.org/docs/16/ddl-partitioning.html) — *official docs* · The first step on the scaling path for `message`.
- [Using EXPLAIN](https://www.postgresql.org/docs/16/using-explain.html) — *official docs* · Read query plans to prove history and resume are index range scans.

### Postgres as a queue

- [Devious SQL: Message Queuing Using Native PostgreSQL](https://www.crunchydata.com/blog/message-queuing-using-native-postgresql) — *Crunchy Data* · `SKIP LOCKED` queue patterns, with the pitfalls; the outbox relay is one of these.

### Migrations

- [Redgate Flyway documentation](https://documentation.red-gate.com/fd/) — *official docs* · Versioned migrations, baseline, validate, repair.
- [Spring Boot — Database initialization with Flyway](https://docs.spring.io/spring-boot/3.5/how-to/data-initialization.html#howto.data-initialization.migration-tool.flyway) — *official docs* · `spring.flyway.*` and how Boot runs it at startup (turned off in `ecs`, run as a migrate task instead).

### Spring, JPA and JDBC

- [Spring Framework — Transaction management](https://docs.spring.io/spring-framework/reference/6.2/data-access/transaction.html) — *official docs* · Declarative transactions, propagation, and the self-invocation proxy trap.
- [Spring Data JPA reference](https://docs.spring.io/spring-data/jpa/reference/3.5/) — *official docs* · Projections, entity graphs, locking annotations.
- [Hibernate ORM 6.6 User Guide](https://docs.hibernate.org/orm/6.6/userguide/html_single/) — *official docs* · Sections "Fetching" and "Locking" (optimistic `@Version`).
- [Optimistic locking with JPA and Hibernate](https://vladmihalcea.com/optimistic-locking-version-property-jpa-hibernate/) — *Vlad Mihalcea* · How `@Version` generates the `WHERE version = ?` check.
- [Spring Framework — JdbcTemplate](https://docs.spring.io/spring-framework/reference/6.2/data-access/jdbc/core.html) — *official docs* · For the outbox relay, pgvector and hot queries.
- [HikariCP — About Pool Sizing](https://github.com/brettwooldridge/HikariCP/wiki/About-Pool-Sizing) — *HikariCP wiki* · Why pools should be small; the formula to start from.

### Aurora

- [Aurora Serverless v2 — scaling to zero ACUs with automatic pause and resume](https://docs.aws.amazon.com/AmazonRDS/latest/AuroraUserGuide/aurora-serverless-v2-auto-pause.html) — *AWS docs* · Resume latency and what keeps a cluster awake (connections).
- [How Aurora Serverless v2 works](https://docs.aws.amazon.com/AmazonRDS/latest/AuroraUserGuide/aurora-serverless-v2.how-it-works.html) — *AWS docs* · ACU ranges and how `max_connections` follows capacity.
- [Password management with Aurora and Secrets Manager](https://docs.aws.amazon.com/AmazonRDS/latest/AuroraUserGuide/rds-secrets-manager.html) — *AWS docs* · The RDS-managed master secret used in P2-T03.
- [Amazon RDS Proxy for Aurora](https://docs.aws.amazon.com/AmazonRDS/latest/AuroraUserGuide/rds-proxy.html) — *AWS docs* · Connection pooling in front of Aurora when tasks scale wide.

### Deeper internals

- [The Internals of PostgreSQL](https://www.interdb.jp/pg/) — *Hironobu Suzuki, free online book* · MVCC, vacuum, WAL, buffer manager.
- [PostgreSQL 14 Internals](https://postgrespro.com/community/books/internals) — *Egor Rogov, free PDF* · Isolation, locks and indexes explained with experiments.

### Books

- *High-Performance Java Persistence* (Vlad Mihalcea) — JDBC batching, connection management, JPA fetching and locking.
- *Designing Data-Intensive Applications*, 2nd ed. — the transactions chapter (isolation anomalies, write skew).
- See [17 — Books and courses](17-books-and-courses.md).

## Self-check

- Two users send to the same channel at the same moment. Walk through the locks and show that the seqs are distinct and in commit order.
- Why must an idempotent retry not bump `last_seq`? How do you do it in one transaction?
- What does `SKIP LOCKED` give up in exchange for parallelism?
- Why is keyset pagination stable when new messages arrive, while `OFFSET` pagination is not?
- What is a lost update, and which two tools prevent it here?
- How many connections do 4 `api` tasks with a Hikari pool of 10 plus 2 workers need? Where would that break on a 0.5 ACU floor?
- What happens to the first request after Aurora auto-pauses, and why is that acceptable only in dev?
