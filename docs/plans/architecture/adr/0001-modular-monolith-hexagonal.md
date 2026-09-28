# 0001 — Hexagonal modular monolith, deployed as `api` + `worker`

- **Status:** Accepted
- **Date:** 2026-09-29
- **Phase:** P2, P6

## Context
The codebase is already hexagonal: `domain/*/port/in|out`, `application/service`, `adapters/in|out`. The roadmap adds latency-sensitive work (REST, WebSocket, RAG streaming) and throughput work (outbox relay, SQS consumers, embeddings). They have different scaling signals. Embedding a backfill must never slow down message delivery.

## Decision drivers
- One developer, 12 weeks.
- Independent scaling for request/response vs background work.
- Keep architecture boundaries enforceable.

## Options considered

### A — One service does everything
- Pros: simplest deploy.
- Cons: a backfill competes with WebSocket delivery for CPU. It scales on the wrong signal.

### B — Same codebase and image, two ECS services selected by Spring profile (`api`, `worker`)
- Pros: one build, one schema, shared domain code. Each service scales on its own metric. `@Profile`/`@ConditionalOnProperty` switches listeners and schedulers on or off.
- Cons: the image carries code each service doesn't use. A bad shared change affects both.

### C — Microservices (chat, presence, ai, notification)
- Pros: independent deploys, per-service tech choice, team autonomy.
- Cons: distributed transactions, N pipelines, service discovery, contract versioning. It solves organizational scaling that a solo project doesn't have.

## Decision
**B.** One Maven module, one image, two ECS services: `api` (`SPRING_PROFILES_ACTIVE=ecs,api`) and `worker` (`ecs,worker`). ArchUnit tests enforce the hexagonal rules so the monolith stays modular.

## Consequences
- Scheduled jobs and SQS listeners must be profile-guarded, or they would run in every `api` task.
- Anything per-process (registry, caches, rate limits) must be designed for N tasks. See `learning/10` §9.
- Extracting a service later is mechanical: its ports already exist.

## Revisit when
- A module needs a different release cadence or runtime (e.g. a Python ML service).
- Build or test time for the monolith exceeds ~10 minutes.
