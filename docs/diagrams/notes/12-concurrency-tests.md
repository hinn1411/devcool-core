# 12 — Testing concurrency on a real Postgres (notes)

Diagram: [../12-concurrency-tests.md](../12-concurrency-tests.md) · Guides: [learning/12 Integration testing](../../learning/12-integration-testing-guide.md)

## Problem
A race-condition fix that's only reasoned about isn't proven. Unit tests with mocks can't show that a row lock serializes, that `ON CONFLICT` waits, or that a blocked UPDATE re-checks its WHERE. Only the real database can.

## Without it
- **Mock-only tests** pass for the naive `MAX(seq) + 1`, because a mock has no concurrency.
- **H2 "in PostgreSQL mode"** uses a different locking model, error codes, `ON CONFLICT`/`RETURNING` support and DDL behaviour, so a green test proves nothing about production.
- **Sequential "concurrency" tests** (call twice in a row) never overlap, so they test the replay path, not the race.

## Mechanism
- **Testcontainers** with `@ServiceConnection`: one `pgvector/pgvector:pg16` container **pinned by digest** (the CI caches the same image), shared by every IT in the JVM. Full-stack ITs extend `AbstractIntegrationTest` (`@SpringBootTest` + MockMvc), and repository ITs extend `AbstractJpaIT` (`@DataJpaTest`).
- **Real overlap:** submit 20 tasks that all block on one `CountDownLatch`, then `countDown()` releases them together, which maximizes the chance they hit the DB at the same time. `future.get(30, SECONDS)` surfaces exceptions and bounds hangs (a deadlock fails the test instead of freezing CI).
- **Assert in the DB, not in memory:** count rows, read `last_seq`, read `deleted_time` with `JdbcTemplate`.
- **N+1 as a test:** enable Hibernate `Statistics` and assert `getPrepareStatementCount() == 1` for a page (`ChannelAdapterIT`, `MessageAdapterIT`, `ReactionAdapterIT`).
- **Migrations as tests:** `freshDatabase(prefix)` creates an empty DB in the shared container, `Flyway…target("4").migrate()` stops at a version, the test seeds rows the way *old code* would, then migrates on and asserts (`V4BackfillIT`, `V5ContractIT`).

## DB concepts this exercises
Row-lock queuing (01), unique-index waits on in-flight inserts (02, 05), the READ COMMITTED re-check (03, 04, 06), transactional DDL and backfills (10).

## Trade-offs
- A race test is probabilistic: if it passes, the race *wasn't observed*, not that it's impossible. That's why every invariant also has a constraint or an atomic statement behind it.
- Shared container + reuse means a test can't assume an empty database. Tests seed their own ids, and migration tests use a fresh DB each.
- Gaps: the refresh-token race (08) and the duplicate-member race (09) don't have a 20-thread test yet.

## Interview Q&A
- **How do you test a race condition?** Make the threads overlap (latch), run against the real engine, assert the invariant from the DB, and bound it with timeouts.
- **Testcontainers vs H2?** Test against what you run: locking, SQL dialect and error codes differ.
- **How do you prove there's no N+1?** Count prepared statements per request in an IT.

## Blog angle
**"20 threads and a latch: testing Postgres race conditions with Testcontainers."**

## Code
- [`PostgresTestContainer`](../../../src/test/java/com/devcool/support/PostgresTestContainer.java), [`AbstractIntegrationTest`](../../../src/test/java/com/devcool/support/AbstractIntegrationTest.java), [`AbstractJpaIT`](../../../src/test/java/com/devcool/support/AbstractJpaIT.java)
- Latch helper: [`SendMessageSeqIT.inParallel`](../../../src/test/java/com/devcool/application/service/chat/SendMessageSeqIT.java#L159)
- Statement counting: [`ChannelAdapterIT.aPageOfChannels_isOneStatement`](../../../src/test/java/com/devcool/adapters/out/persistence/channel/ChannelAdapterIT.java#L110)
