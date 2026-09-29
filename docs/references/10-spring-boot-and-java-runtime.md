# 10 — Spring Boot and the Java runtime

> **Used in DevCool:** [02 — Tech stack, Backend](../plans/architecture/02-tech-stack.md#backend) · [Phase 1](../plans/phases/phase-1-foundation-hardening.md) (P1-T10 actuator + graceful shutdown, P1-T15 structured logging, P1-T16 virtual threads) · P2-T05–T06 (profiles, Dockerfile) · P4-T14 (drain on `ContextClosedEvent`) · P8-T16 (Resilience4j around Bedrock) · [03 §6](../plans/architecture/03-chat-system-design.md#6-fan-out-across-nodes) (delivery executor)
> **Links checked:** 2026-09-29 · Versions assumed: Java 21, Spring Boot 3.5

## Concepts to own

- **Virtual threads.** Cheap threads scheduled by the JVM onto a few carrier threads; blocking I/O unmounts them. They make "one thread per blocking call" affordable for many sockets and JDBC/S3/Bedrock calls. *In DevCool:* `spring.threads.virtual.enabled=true`.
- **Pinning (Java 21).** A virtual thread blocking inside `synchronized` (or a native frame) pins its carrier. On Java 21, prefer `ReentrantLock` around blocking I/O. JEP 491 (Java 24) removes the `synchronized` case. *In DevCool:* P1-T16's check on the WS send path.
- **Graceful shutdown and lifecycle phases.** On SIGTERM, Spring stops accepting requests, waits for in-flight ones (`timeout-per-shutdown-phase`), then closes beans. `ContextClosedEvent` is where the WS drain hooks in.
- **Liveness vs readiness.** Liveness = "restart me if false"; readiness = "don't send me traffic". Readiness goes `REFUSING_TRAFFIC` on shutdown. A dependency outage (Valkey) should not fail liveness.
- **Structured logging.** JSON logs (ECS format) with `trace_id`/`span_id`; MDC carries `userId`/`connectionId`. Boot 3.4+ has it built in.
- **Profiles as runtime switches.** `local`, `ecs`, `api`, `worker`, `migrate`. `@Scheduled` jobs and listeners only in the right profile ([01](01-architecture-hexagonal-modular-monolith.md)).
- **Resilience patterns.** Timeouts first, then retries (only for idempotent calls, with backoff), circuit breaker (stop calling a failing dependency), bulkhead (cap concurrency), rate limiter. Each has metrics.
- **Container-aware JVM.** `-XX:MaxRAMPercentage=75` sizes the heap from the container limit; leave headroom for metaspace, thread stacks, direct buffers and the sidecar.
- **Layered jars.** Split dependencies from app classes so Docker layer caching makes image builds fast.
- **API contracts.** springdoc generates `/v3/api-docs`, which feeds the frontend's TypeScript client ([14](14-frontend-react-spa.md)).

## Read first

1. [JEP 444: Virtual Threads](https://openjdk.org/jeps/444) — *OpenJDK* · The design, the "don't pool virtual threads" advice, and the pinning section.
2. [Oracle — Virtual Threads guide (Java 21)](https://docs.oracle.com/en/java/javase/21/core/virtual-threads.html) — *official docs* · Adoption guide, detecting pinning with JFR (`jdk.VirtualThreadPinned`).
3. [Spring Boot — Kubernetes probes (liveness/readiness)](https://docs.spring.io/spring-boot/3.5/reference/actuator/endpoints.html#actuator.endpoints.kubernetes-probes) — *official docs* · Health groups and how to include/exclude dependencies. The same endpoints serve ALB health checks.
4. [Spring Boot — Graceful shutdown](https://docs.spring.io/spring-boot/3.5/reference/web/graceful-shutdown.html) — *official docs* · `server.shutdown=graceful` and the shutdown phase timeout.
5. [Resilience4j — Getting started with Spring Boot 3](https://resilience4j.readme.io/docs/getting-started-3) — *official docs* · Annotations, config per instance, and metrics.

## Reference

### Java runtime

- [JEP 491: Synchronize Virtual Threads without Pinning](https://openjdk.org/jeps/491) — *OpenJDK* · Fixed in Java 24; explains exactly what pins on Java 21.
- [Embracing Virtual Threads](https://spring.io/blog/2022/10/11/embracing-virtual-threads) — *Spring blog* · How Spring adopted virtual threads and what to watch for.
- [The `java` command (Java 21)](https://docs.oracle.com/en/java/javase/21/docs/specs/man/java.html) — *official docs* · `MaxRAMPercentage`, `JAVA_TOOL_OPTIONS`, `-javaagent` (OTel agent in [12](12-observability.md)).

### Spring Boot features used by the plan

- [Spring Boot — Virtual threads](https://docs.spring.io/spring-boot/3.5/reference/features/spring-application.html#features.spring-application.virtual-threads) — *official docs* · What the property switches (Tomcat, `@Async`, schedulers) and the daemon-thread caveat.
- [Spring Boot — Application availability](https://docs.spring.io/spring-boot/3.5/reference/features/spring-application.html#features.spring-application.application-availability) — *official docs* · `LivenessState` / `ReadinessState` and publishing state changes.
- [Liveness and Readiness Probes with Spring Boot](https://spring.io/blog/2020/03/25/liveness-and-readiness-probes-with-spring-boot) — *Spring blog* · The reasoning for what belongs in each probe.
- [Spring Boot — Structured logging](https://docs.spring.io/spring-boot/3.5/reference/features/logging.html#features.logging.structured) — *official docs* · `logging.structured.format.console=ecs` and custom fields.
- [SLF4J — Mapped Diagnostic Context](https://www.slf4j.org/manual.html#mdc) — *official docs* · MDC for `userId`/`connectionId`; remember MDC is thread-local when handing work to executors.
- [Spring Boot — Task execution and scheduling](https://docs.spring.io/spring-boot/3.5/reference/features/task-execution-and-scheduling.html) — *official docs* · `@Scheduled` pools, and the bounded executor for pub/sub delivery.
- [Spring Boot — Efficient container images](https://docs.spring.io/spring-boot/3.5/reference/packaging/container-images/efficient-images.html) and [Dockerfiles](https://docs.spring.io/spring-boot/3.5/reference/packaging/container-images/dockerfiles.html) — *official docs* · Layered jars and a multi-stage Dockerfile (P2-T06).

### Resilience

- [CircuitBreaker](https://martinfowler.com/bliki/CircuitBreaker.html) — *Martin Fowler* · The pattern and its states.
- Resilience4j modules: [CircuitBreaker](https://resilience4j.readme.io/docs/circuitbreaker), [Retry](https://resilience4j.readme.io/docs/retry), [TimeLimiter](https://resilience4j.readme.io/docs/timeout), [Bulkhead](https://resilience4j.readme.io/docs/bulkhead) — *official docs* · The four used around Bedrock: 5 s to first token, 30 s total, retry once on throttling, open at 50% failures.
- [Timeouts, retries, and backoff with jitter](https://aws.amazon.com/builders-library/timeouts-retries-and-backoff-with-jitter/) — *Amazon Builders' Library* · Why retries need budgets and why only idempotent calls should retry.

### API and mapping

- [springdoc-openapi](https://springdoc.org/) — *official docs* · Annotations and config for accurate `/v3/api-docs`.
- [MapStruct reference guide](https://mapstruct.org/documentation/stable/reference/html/) — *official docs* · Mapping lazy associations is graph traversal (see learning/01).

### Security framework pieces

- [Spring Security — Servlet architecture](https://docs.spring.io/spring-security/reference/6.5/servlet/architecture.html) — *official docs* · The filter chain `JwtAuthFilter` plugs into.
- [Nimbus JOSE + JWT](https://connect2id.com/products/nimbus-jose-jwt) — *official site* · The JWT library DevCool uses; examples for signing and validation.

### Books

- *Release It!*, 2nd ed. (Michael Nygard) — stability patterns: timeouts, circuit breakers, bulkheads, steady state.
- See [17 — Books and courses](17-books-and-courses.md).

## Self-check

- What does "pinning" mean, and why does it matter more for a WebSocket server than for a typical REST app?
- Why should a Valkey outage not make the liveness probe fail? What should readiness do?
- Walk through what happens between SIGTERM and process exit with `server.shutdown=graceful` and the P4-T14 drain.
- Why should you never retry a non-idempotent call, and which Bedrock call is safe to retry only *before* the first token?
- What's the difference between a time limiter and a circuit breaker? Why do you need both?
- Why is MDC data missing in logs from an executor thread, and how do you fix it?
