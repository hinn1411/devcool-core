# DevCool — Reference library

Curated reading for the [fullstack roadmap](../plans/README.md). The plans say *what* to build and *why this option*; these files point to the sources that explain *how it works*, so you can go deep on any topic later.

Each topic file has the same four parts:

| Section | Use it for |
|---|---|
| **Concepts to own** | A short primer per concept, and where it shows up in DevCool (ADR, design section, task id) |
| **Read first** | 3–5 sources, in order. Enough to implement the phase |
| **Reference** | The fuller list, grouped by sub-topic, with one line on what each source gives you |
| **Self-check** | Questions to answer from memory before an interview or a PR |

Related material already in the repo: [`docs/learning/`](../learning/README.md) (audit lessons about *this* codebase) and [`docs/improvements/lessons.md`](../improvements/lessons.md). The reference files link to them where they overlap.

## Index

| # | File | Covers | ADRs | Phases |
|---|---|---|---|---|
| 01 | [Hexagonal architecture and the modular monolith](01-architecture-hexagonal-modular-monolith.md) | Ports and adapters, dependency rule, modular monolith, profiles, ArchUnit, ADRs, monorepo | 0001, 0004 | P1, P6 |
| 02 | [Chat system design](02-chat-system-design.md) | Estimation, ordering, idempotency, delivery semantics, presence, receipts, scaling path | 0012 | P3, P4 |
| 03 | [WebSocket and the realtime protocol](03-websocket-realtime-protocol.md) | RFC 6455, close codes, backpressure, heartbeat, backoff + jitter, drain, tickets, CSWSH | 0005, 0011 | P4, P5 |
| 04 | [Redis / Valkey](04-redis-valkey.md) | Pub/sub and sharded pub/sub, TTL presence, `GETDEL`, ElastiCache Serverless limits, Bucket4j, locks | 0006, 0011 | P4, P8 |
| 05 | [PostgreSQL, Aurora and data access](05-postgresql-and-data-access.md) | Row locks, `SKIP LOCKED`, `ON CONFLICT`, isolation, keyset pagination, FTS, Flyway, JPA, Hikari, Aurora Serverless v2 | 0009, 0012 | P1, P3, P6 |
| 06 | [Event-driven backbone: outbox → SNS → SQS](06-event-driven-outbox-sns-sqs.md) | Dual write, outbox, idempotent consumers, filter policies, visibility timeout, DLQ/redrive, FIFO, CDC | 0007 | P6, P8 |
| 07 | [AWS compute, edge and networking](07-aws-compute-edge-networking.md) | ECS Fargate, roles, deploys and drain, ALB, autoscaling, CloudFront policies, VPC/endpoints, secrets, cost | 0002, 0011 | P2, P4, P9 |
| 08 | [Terraform and IaC](08-terraform-iac.md) | S3 backend + `use_lockfile`, layered stacks, remote state, modules, `lifecycle`, tflint/Trivy | 0003 | P2, P6, P7, P8 |
| 09 | [CI/CD and supply chain](09-cicd-and-supply-chain.md) | GitHub OIDC → AWS, environments, path filters, ECS deploy actions, static analysis, Dependabot, Trivy | 0004 | P0, P1, P2, P5 |
| 10 | [Spring Boot and the Java runtime](10-spring-boot-and-java-runtime.md) | Virtual threads and pinning, probes, graceful shutdown, structured logs, Resilience4j, container JVM | 0001, 0010 | P1, P2, P4, P8 |
| 11 | [Application security](11-security.md) | BOLA/IDOR, property-level authz, JWT and refresh rotation, SameSite cookies, CORS, OWASP cheat sheets | 0011 | P1, P9 |
| 12 | [Observability](12-observability.md) | OpenTelemetry agent + Collector, trace context across SNS/SQS, cardinality, histograms, RED/USE, SLOs | 0008 | P6, P7 |
| 13 | [GenAI: RAG, pgvector, Bedrock, Spring AI](13-genai-rag-bedrock.md) | Chunking, HNSW + iterative scans, permission-aware retrieval, Converse streaming, prompt injection, evals | 0009, 0010 | P8 |
| 14 | [Frontend: React SPA with a realtime client](14-frontend-react-spa.md) | TanStack Query + WS events, optimistic send, virtualized reverse scroll, generated API types, MSW, Playwright | 0011 | P5, P8 |
| 15 | [Testing, load testing and failure testing](15-testing-and-load.md) | Testcontainers, concurrency ITs, ArchUnit, k6 WebSockets, coordinated omission, chaos experiments | — | P1, P3, P4, P6, P8, P9 |
| 16 | [Claude Code setup](16-claude-code.md) | Memory and rules, settings and permissions, hooks, skills, subagents, MCP, plugins, GitHub Action | 0004 | P0 |
| 17 | [Books, courses and certifications](17-books-and-courses.md) | The consolidated book list with the chapters that matter, free courses, AWS/HashiCorp exam guides | — | all |

## Before each phase, read

The "Read first" section of these files, in this order.

| Phase | Files |
|---|---|
| [P0 Claude Code setup](../plans/phases/phase-0-claude-code-setup.md) | [16](16-claude-code.md), [09](09-cicd-and-supply-chain.md) (claude-code-action only) |
| [P1 Foundation hardening](../plans/phases/phase-1-foundation-hardening.md) | [11](11-security.md), [05](05-postgresql-and-data-access.md) (Flyway, JPA), [15](15-testing-and-load.md) (Testcontainers, ArchUnit), [10](10-spring-boot-and-java-runtime.md) (probes, virtual threads, logging), [01](01-architecture-hexagonal-modular-monolith.md) (ArchUnit rules), [09](09-cicd-and-supply-chain.md) (CI) |
| [P2 Infra + CI/CD walking skeleton](../plans/phases/phase-2-infra-cicd-walking-skeleton.md) | [08](08-terraform-iac.md), [07](07-aws-compute-edge-networking.md), [09](09-cicd-and-supply-chain.md), [05](05-postgresql-and-data-access.md) (Aurora part) |
| [P3 Core chat](../plans/phases/phase-3-core-chat.md) | [02](02-chat-system-design.md), [05](05-postgresql-and-data-access.md) |
| [P4 Realtime at scale](../plans/phases/phase-4-realtime-at-scale.md) | [03](03-websocket-realtime-protocol.md), [04](04-redis-valkey.md), [02](02-chat-system-design.md) (presence, typing, resume), [07](07-aws-compute-edge-networking.md) (drain, autoscaling) |
| [P5 React frontend](../plans/phases/phase-5-frontend.md) | [14](14-frontend-react-spa.md), [03](03-websocket-realtime-protocol.md) (client side), [11](11-security.md) (tokens in the browser) |
| [P6 Async events](../plans/phases/phase-6-async-events.md) | [06](06-event-driven-outbox-sns-sqs.md), [05](05-postgresql-and-data-access.md) (`SKIP LOCKED`), [12](12-observability.md) (trace context) |
| [P7 Observability](../plans/phases/phase-7-observability.md) | [12](12-observability.md), [08](08-terraform-iac.md) (alerts as code) |
| [P8 Ask DevCool](../plans/phases/phase-8-genai.md) | [13](13-genai-rag-bedrock.md), [06](06-event-driven-outbox-sns-sqs.md) (indexer consumer), [10](10-spring-boot-and-java-runtime.md) (Resilience4j), [04](04-redis-valkey.md) (rate limits, budget) |
| [P9 Load test and polish](../plans/phases/phase-9-load-test-polish.md) | [15](15-testing-and-load.md), [07](07-aws-compute-edge-networking.md) (cost), [11](11-security.md) (security pass), [17](17-books-and-courses.md) (interview prep) |

## ADR → reference files

| ADR | Files |
|---|---|
| [0001 Modular monolith, api + worker](../plans/architecture/adr/0001-modular-monolith-hexagonal.md) | [01](01-architecture-hexagonal-modular-monolith.md), [10](10-spring-boot-and-java-runtime.md) |
| [0002 ECS Fargate](../plans/architecture/adr/0002-ecs-fargate.md) | [07](07-aws-compute-edge-networking.md) |
| [0003 Terraform layered stacks](../plans/architecture/adr/0003-terraform-layered-stacks.md) | [08](08-terraform-iac.md) |
| [0004 Monorepo](../plans/architecture/adr/0004-monorepo.md) | [01](01-architecture-hexagonal-modular-monolith.md), [09](09-cicd-and-supply-chain.md), [16](16-claude-code.md) |
| [0005 Raw WebSocket, protocol v2](../plans/architecture/adr/0005-raw-websocket-protocol-v2.md) | [03](03-websocket-realtime-protocol.md) |
| [0006 Redis pub/sub backplane](../plans/architecture/adr/0006-redis-pubsub-backplane.md) | [04](04-redis-valkey.md), [02](02-chat-system-design.md) |
| [0007 Outbox → SNS → SQS](../plans/architecture/adr/0007-event-backbone-outbox-sns-sqs.md) | [06](06-event-driven-outbox-sns-sqs.md), [05](05-postgresql-and-data-access.md) |
| [0008 OTel → Grafana Cloud](../plans/architecture/adr/0008-observability-otel-grafana-cloud.md) | [12](12-observability.md) |
| [0009 Aurora + pgvector](../plans/architecture/adr/0009-aurora-postgres-pgvector.md) | [05](05-postgresql-and-data-access.md), [13](13-genai-rag-bedrock.md) |
| [0010 Bedrock via Spring AI](../plans/architecture/adr/0010-bedrock-spring-ai.md) | [13](13-genai-rag-bedrock.md), [10](10-spring-boot-and-java-runtime.md) |
| [0011 WS tickets, single origin](../plans/architecture/adr/0011-ws-ticket-auth-single-origin.md) | [03](03-websocket-realtime-protocol.md), [07](07-aws-compute-edge-networking.md), [11](11-security.md), [14](14-frontend-react-spa.md) |
| [0012 Per-channel seq](../plans/architecture/adr/0012-message-ordering-per-channel-seq.md) | [02](02-chat-system-design.md), [05](05-postgresql-and-data-access.md) |

## Open "verify" items in the plans → where to check

The plans leave some facts to confirm at implementation time. These are the pages that answer them. What the docs said on 2026-09-29 is noted, but re-check at the time: services change.

| Item in the plans | Where to check | Noted on 2026-09-29 |
|---|---|---|
| ADR-0006 / P4-T05: does ElastiCache Serverless support the pub/sub commands used? | [ElastiCache — Supported and restricted commands](https://docs.aws.amazon.com/AmazonElastiCache/latest/dg/SupportedCommands.html) | Serverless implements `PUBLISH`/`SUBSCRIBE` with sharded pub/sub internally; `PSUBSCRIBE`/`PUNSUBSCRIBE` are not available; `CONFIG` is restricted (matters for keyspace notifications). See [04](04-redis-valkey.md) |
| ADR-0009 / P8 start: pgvector ≥ 0.8 on the chosen Aurora version? | [Aurora PostgreSQL extension versions](https://docs.aws.amazon.com/AmazonRDS/latest/AuroraPostgreSQLReleaseNotes/AuroraPostgreSQL.Extensions.html), [pgvector 0.8.0 on Aurora announcement](https://aws.amazon.com/about-aws/whats-new/2025/04/pgvector-0-8-0-aurora-postgresql) | pgvector 0.8.0 is available from Aurora PostgreSQL 16.8 (and 15.12, 14.17, 13.20). Confirm with `SELECT extversion FROM pg_extension WHERE extname='vector'` |
| ADR-0010 / 02-tech-stack: which Spring AI line supports Boot 3.5? | [Spring AI 1.1 reference](https://docs.spring.io/spring-ai/reference/1.1/api/chatclient.html), context7 | Spring AI 1.1.x targets Boot 3.5; 2.x requires Boot 4. Use the `/reference/1.1/` docs |
| ADR-0010: model access and ids in `ap-southeast-1` | [Supported models by Region](https://docs.aws.amazon.com/bedrock/latest/userguide/models-regions.html), [Cross-Region inference](https://docs.aws.amazon.com/bedrock/latest/userguide/cross-region-inference.html), [Model access](https://docs.aws.amazon.com/bedrock/latest/userguide/model-access.html) | Check in the console at P8 start; request quota increases early ([quotas](https://docs.aws.amazon.com/bedrock/latest/userguide/quotas.html)) |
| 01 §5.4 / ADR-0009: Aurora resume time after auto-pause | [Scaling to zero ACUs with auto-pause](https://docs.aws.amazon.com/AmazonRDS/latest/AuroraUserGuide/aurora-serverless-v2-auto-pause.html) | — |
| 02-tech-stack: "WS mocking needs a small fake server" | [MSW — Mocking WebSocket](https://mswjs.io/docs/websocket/) | MSW supports WebSocket interception, which may remove the need for a fake server |

## Conventions for adding a reference

- Prefer, in order: official docs, specs and RFCs → vendor engineering blogs (AWS, Discord, Slack, Grafana) → well-known individual authors → books.
- Give each link one line saying what to take from it, and which section if the page is long.
- Match the version the project runs (Spring Boot 3.5, Java 21, PostgreSQL 16, Spring AI 1.1, JUnit 5.12). Use a versioned URL when "current" points at a newer major.
- Update the **Links checked** date in the file when you re-check it.

Re-check every external link (prints only failures). Medium-hosted posts (including the Netflix Tech Blog) and O'Reilly return 403 to scripts; open those in a browser. GitHub may return 429 when checked in bulk; re-run later.

```bash
grep -ohE 'https?://[^) ]+' docs/references/*.md | sort -u \
  | xargs -P 8 -I{} sh -c 'c=$(curl -s -o /dev/null -L --max-time 20 -A "Mozilla/5.0" -w "%{http_code}" "{}"); [ "$c" = 200 ] || echo "$c {}"'
```
