# 15 — Testing, load testing and failure testing

> **Used in DevCool:** [02 — Tech stack, Testing](../plans/architecture/02-tech-stack.md#testing) · [Phase 1](../plans/phases/phase-1-foundation-hardening.md) (P1-T11–T13: Testcontainers, first ITs, ArchUnit) · P3-T03 (concurrent retry race IT) · P4-T16 (multi-node WS IT) · P6-T07 (idempotency/ordering/poison tests) · P8-T10, T19–T20 (leak test, eval, injection tests) · [Phase 9](../plans/phases/phase-9-load-test-polish.md) (k6, chaos)
> **Already in the repo:** [learning/06](../learning/06-testing-and-verification.md), [07](../learning/07-unit-testing-guide.md), [08](../learning/08-unit-testing-exercises.md), [09](../learning/09-test-schools.md) · [.claude/rules/testing.md](../../.claude/rules/testing.md) · the `test-writer` agent
> **Links checked:** 2026-09-29 · Versions assumed: JUnit 5.12 (Spring Boot 3.5), Testcontainers 1.x

## Concepts to own

- **Test at the layer where the bug lives.** Mockito unit tests for service rules and authorization branches; Testcontainers ITs for SQL, locking, constraints, Redis and SQS behaviour that mocks can't show.
- **Real dependencies in ITs.** `pgvector/pgvector:pg16`, `valkey/valkey`, LocalStack, wired with `@ServiceConnection`; reused containers keep the suite fast.
- **Concurrency tests.** Two threads racing the same `clientMsgId`; two Spring contexts on random ports sharing one Valkey to prove cross-node delivery. Use latches and Awaitility, never `Thread.sleep`.
- **Property-style event tests.** Deliver the same event twice → one effect; deliver v3 then v2 → final state v3; poison message → DLQ.
- **Architecture tests.** ArchUnit rules fail the build when the domain imports Spring/JPA/AWS ([01](01-architecture-hexagonal-modular-monolith.md)).
- **Tags for slow suites.** `@Tag("eval")` excluded from normal CI, run on dispatch.
- **Load test models.** Open model (arrival rate, like real users) vs closed model (fixed VUs that wait on responses). WebSocket soak, chat mix, reconnect storm, REST paging.
- **Measuring latency honestly.** Coordinated omission makes closed-loop tools under-report tail latency; record full histograms (HdrHistogram) and report p99/p99.9.
- **Chaos experiments.** Hypothesis → inject failure (kill a task, cut Valkey, stop the worker, deny Bedrock) → observe the steady-state metric → document.

## Read first

1. [Spring Boot — Testcontainers](https://docs.spring.io/spring-boot/3.5/reference/testing/testcontainers.html) — *official docs* · `@ServiceConnection`, container beans, and using Testcontainers at dev time.
2. [Testcontainers for Java](https://java.testcontainers.org/) — *official docs* · Lifecycle, networking, and the [Postgres](https://java.testcontainers.org/modules/databases/postgres/) and [LocalStack](https://java.testcontainers.org/modules/localstack/) modules.
3. [The Practical Test Pyramid](https://martinfowler.com/articles/practical-test-pyramid.html) — *Ham Vocke, martinfowler.com* · What belongs at each level, with Spring examples.
4. [k6 — WebSockets (`k6/websockets`)](https://grafana.com/docs/k6/latest/javascript-api/k6-websockets/) — *official docs* · The event-loop-based WS API for P9-T01.
5. [Principles of Chaos Engineering](https://principlesofchaos.org/) — *community manifesto* · The hypothesis-driven method for P9-T04–T07.

## Reference

### Unit and integration testing

- [JUnit 5.12 User Guide](https://docs.junit.org/5.12.2/user-guide/) — *official docs* · Tags and filtering, parameterized tests, extensions. (JUnit 6 is current upstream; Boot 3.5 ships 5.12.)
- [Mockito](https://site.mockito.org/) — *official site* · Stubbing, `ArgumentCaptor`, strict stubs with `MockitoExtension`.
- [AssertJ](https://assertj.github.io/doc/) — *official docs* · Fluent assertions, `extracting`, soft assertions.
- [Spring Boot — Testing](https://docs.spring.io/spring-boot/3.5/reference/testing/index.html) — *official docs* · Test slices, `@SpringBootTest` with random ports.
- [Spring Framework — MockMvc](https://docs.spring.io/spring-framework/reference/6.2/testing/mockmvc.html) — *official docs* · Controller ITs for 401/403 and cookie flows (P1-T12).
- [Awaitility usage](https://github.com/awaitility/awaitility/wiki/Usage) — *wiki* · Waiting for async effects (SQS consumers, pub/sub delivery) without sleeps.
- [Testing Spring Boot REST API using Testcontainers](https://testcontainers.com/guides/testing-spring-boot-rest-api-using-testcontainers/) — *Testcontainers guide* · A complete worked example.
- [ArchUnit User Guide](https://www.archunit.org/userguide/html/000_Index.html) — *official docs* · See [01](01-architecture-hexagonal-modular-monolith.md).
- [oasdiff](https://github.com/oasdiff/oasdiff) — *README* · OpenAPI breaking-change detection for the contract check in CI.

### Load testing with k6

- [k6 documentation](https://grafana.com/docs/k6/latest/) — *official docs* · Start with "Get started" and "Using k6".
- [Scenarios](https://grafana.com/docs/k6/latest/using-k6/scenarios/) and [executors](https://grafana.com/docs/k6/latest/using-k6/scenarios/executors/) — *official docs* · Ramping VUs vs constant arrival rate.
- [Open and closed models](https://grafana.com/docs/k6/latest/using-k6/scenarios/concepts/open-vs-closed/) — *official docs* · Why arrival-rate executors avoid coordinated omission.
- [Thresholds](https://grafana.com/docs/k6/latest/using-k6/thresholds/) — *official docs* · Pass/fail on p99 delivery latency.
- [Test lifecycle (setup/teardown)](https://grafana.com/docs/k6/latest/using-k6/test-lifecycle/) — *official docs* · Fetching tickets in `setup()` (P9-T01).

### Measuring latency

- [How NOT to Measure Latency](https://www.youtube.com/watch?v=lJ8ydIuPFeU) — *Gil Tene, talk* · Coordinated omission and why averages and "max of p99s" lie.
- [HdrHistogram](https://hdrhistogram.github.io/HdrHistogram/) — *project site* · The histogram most latency tools use.

### Failure testing

- [AWS Fault Injection Service](https://docs.aws.amazon.com/fis/latest/userguide/what-is.html) — *AWS docs* · Managed experiments (stop ECS tasks, network disruption) if you want P9 chaos tests repeatable.

### Books

- *Unit Testing Principles, Practices, and Patterns* (Vladimir Khorikov) — the classical vs London schools behind learning/09.
- See [17 — Books and courses](17-books-and-courses.md).

## Self-check

- Which DevCool behaviours can only be tested with a real Postgres? Name three.
- How do you write a deterministic test for "two concurrent retries with the same `clientMsgId` produce one message and one seq bump"?
- How does the multi-node WS IT prove delivery went through Valkey and not a shortcut?
- What is coordinated omission, and which k6 executor avoids it?
- For the "kill one api task" experiment: what is the hypothesis, the steady-state metric, and the pass criterion?
- Why are eval tests tagged and excluded from normal CI?
