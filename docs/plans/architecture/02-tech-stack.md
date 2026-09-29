# 02 — Technology Stack

This doc lists each concern, the choice, the main alternatives and why. Decisions with long-lived consequences have an ADR; this page is the overview.

> **Versions move.** Before adding a dependency, check its current version and compatibility with the context7 MCP (`use context7`), e.g. which Spring AI line supports Spring Boot 3.5. The versions below are what the plan assumes, not pins.

## Backend

| Concern | Choice | Alternatives | Pros of the choice | Cons / watch-outs |
|---|---|---|---|---|
| Language/runtime | **Java 21**, virtual threads on (`spring.threads.virtual.enabled=true`) | Kotlin, Java 25 | Already in use. Virtual threads make blocking JDBC/S3/Bedrock calls cheap under many sockets | Pinning inside `synchronized` blocks; check JDBC driver and library behaviour under load |
| Framework | **Spring Boot 3.5** | Boot 4 / Spring 7, Quarkus, Micronaut | Existing code; Spring AI, Spring Data, Security integrate natively | Boot 4 migration later (Jakarta EE 11, new Spring AI line). Not in this roadmap |
| Architecture | **Hexagonal modular monolith** (ADR-0001) | Layered MVC, microservices | Already in place; ports make adapters swappable and testable | More files per feature. Guard it with ArchUnit, not only reviews |
| Mapping | **MapStruct** | Manual mappers | Already migrated; compile-time, fast | Annotation-processor ordering with Lombok (already configured) |
| DB access | **Spring Data JPA** for aggregates, **JdbcTemplate** for pgvector and hot queries | jOOQ, MyBatis | JPA is already used; JdbcTemplate gives full SQL control where JPA is weak (vectors, `SKIP LOCKED`, `RETURNING`) | Two styles in one codebase; keep them behind the same ports |
| Migrations | **Flyway** | Liquibase | Plain SQL, already on the classpath | Never edit a merged migration (enforced by a hook) |
| Realtime transport | **Raw WebSocket + typed JSON envelope** (ADR-0005) | STOMP, SSE + POST, Socket.IO, API Gateway WebSocket API | Existing handler; full control of the protocol; easy to explain | You write heartbeat, ACK, resume yourself (that's also the interview material) |
| Realtime backplane | **Valkey/Redis pub/sub** (ADR-0006) | PG `LISTEN/NOTIFY`, SNS → SQS per node, Kafka | Sub-ms, simple, the same Redis serves presence, tickets, rate limits | At-most-once. Recovered by `RESUME` |
| Async events | **Transactional outbox → SNS → SQS** (ADR-0007) | Kafka/MSK, EventBridge, Redis Streams, Debezium | Managed, scales to zero, DLQs built in, at-least-once | No replay beyond SQS retention; standard queues are unordered (consumers use `version`) |
| Resilience | **Resilience4j** (retry, circuit breaker, bulkhead, time limiter) | Spring Retry, hand-written | Standard, Micrometer metrics included | Configure per dependency; don't retry non-idempotent calls |
| Rate limiting | **Bucket4j with the Redis (Lettuce) backend** | Redis `INCR` + TTL scripts, API Gateway throttling | Token bucket, shared across tasks, per user/per feature | Another dependency; learn its proxy-manager API |
| Auth | **nimbus-jose-jwt** (existing) | Spring Authorization Server, Cognito | Already built; you understand every line | You own key rotation. Cognito is an option if social login grows |
| API docs | **springdoc-openapi** (existing) | — | `/v3/api-docs` feeds the frontend's generated TypeScript client | Keep annotations accurate or the client drifts |

## Data

| Concern | Choice | Alternatives | Pros | Cons |
|---|---|---|---|---|
| Primary DB | **Aurora Serverless v2, PostgreSQL 16** (ADR-0009) | RDS PostgreSQL, DynamoDB, Cassandra/ScyllaDB | Relational model fits channels/members; transactions for seq + outbox; scale-to-zero in dev | Cold start after auto-pause; one writer |
| Vector store | **pgvector** in the same DB (ADR-0009) | OpenSearch Serverless, Pinecone, Qdrant, Redis vector search | Permission filter is ordinary SQL; transactional with chunk metadata; nothing new to run | Filtered ANN needs care (`hnsw.iterative_scan`); fine up to millions of vectors, not billions |
| Cache/ephemeral | **ElastiCache Serverless for Valkey** | ElastiCache node-based, MemoryDB, Upstash | No node sizing; pub/sub, TTL keys, Lua; Valkey is the open-source Redis fork AWS prices lower | Minimum monthly charge even when idle; verify pub/sub behaviour on serverless during P4 |
| Object storage | **S3** + presigned PUT/GET | — | Existing; browser uploads direct, API never streams bytes | Validate `Content-Type`/size in the presign policy |
| Keyword search | **PostgreSQL full-text** (`tsvector`, GIN) | OpenSearch | Zero new infra; hybrid with vectors later (RRF) | Weaker relevance and language analysis than OpenSearch |

## GenAI

| Concern | Choice | Alternatives | Pros | Cons |
|---|---|---|---|---|
| Model provider | **Amazon Bedrock** (ADR-0010) | Anthropic API direct, OpenAI | IAM task role (no API keys), VPC endpoint, AWS GenAI certification alignment | Model availability per region; newest models may arrive on the direct API first |
| Generation model | **Claude Sonnet** for RAG, **Claude Haiku** for summaries/tags | — | Quality where it matters, cheap where it doesn't | Check the model ids available in `ap-southeast-1` (or use a cross-region inference profile) |
| Embeddings | **Amazon Titan Text Embeddings v2, 1024 dims** | Cohere Embed v3 (multilingual), OpenAI | Cheap, configurable dims | Changing the model means re-indexing everything; store `embedding_model` per row |
| Java LLM framework | **Spring AI** (Bedrock Converse + embedding clients) | LangChain4j, raw AWS SDK | Spring-native config, streaming `Flux`, observations for token usage | Fast-moving API; pin the version compatible with Boot 3.5 |
| Local LLM | **Ollama** (`mxbai-embed-large` 1024-d + a small chat model) | Bedrock in dev | Free, offline, same dims as Titan v2 | Answer quality differs from Claude; don't evaluate on it |
| Evaluation | Golden set + recall@k + LLM-as-judge (in-repo JUnit tagged `eval`) | Ragas, DeepEval, Langfuse | No new platform; runs in CI on demand | You maintain the judge prompt |

## Frontend

| Concern | Choice | Alternatives | Pros | Cons |
|---|---|---|---|---|
| Framework | **React 19 + Vite + TypeScript** | Next.js, SvelteKit | Pure SPA deploys to S3; fast dev server; matches "simple frontend" | No SSR (not needed behind auth) |
| Server state | **TanStack Query** (infinite queries for history) | RTK Query, SWR | Cache, retries, pagination primitives | Must integrate WS pushes into the cache by hand (`setQueryData`) |
| Client state | **Zustand** (connection state, presence, typing) | Redux Toolkit, Context | Tiny, no boilerplate | Discipline to keep server data in Query, not here |
| Routing | **React Router** | TanStack Router | Familiar | — |
| UI | **Tailwind CSS + shadcn/ui** | MUI, Chakra | Owned components, accessible primitives (Radix) | You maintain the copied components |
| Long lists | **react-virtuoso** | react-window, TanStack Virtual | Reverse infinite scroll and variable heights built in | One more dependency |
| API client | **openapi-typescript + openapi-fetch** from `/v3/api-docs` | Orval, hand-written | Types stay in sync with the backend | Regenerate on API change (CI check) |
| Tests | **Vitest + React Testing Library + MSW**, **Playwright** for smoke E2E | Jest, Cypress | Vite-native, fast; MSW mocks REST in tests | WS mocking needs a small fake server |

## Platform

| Concern | Choice | Alternatives | Pros | Cons |
|---|---|---|---|---|
| Compute | **ECS Fargate** (ADR-0002) | EKS, App Runner, Lambda + API Gateway WS | No cluster to run; ALB supports WS; desired count 0 when idle | Less portable than Kubernetes; fewer knobs |
| IaC | **Terraform**, layered stacks (ADR-0003) | AWS CDK, Pulumi, CloudFormation | Most requested in job posts; plan/apply review flow; huge module ecosystem | State management is on you (S3 backend + native lockfile) |
| CI/CD | **GitHub Actions** + OIDC to AWS | CodePipeline, GitLab CI | Already used; no long-lived keys; environments with approvals | Minutes limits on private repos |
| Container registry | **ECR** with scan on push + lifecycle policy | GHCR | IAM-native pulls from ECS | — |
| Security scanning | **Trivy** (image + IaC), **Checkov**/**tflint**, Dependabot | Snyk | Free, CI-friendly | Tune noise with ignore files, don't disable |
| Secrets | **Secrets Manager** (DB via RDS-managed secret, JWT keys, Grafana token) | SSM Parameter Store (SecureString) | Rotation support, ECS `secrets` injection | ~$0.40/secret/month; use SSM for non-secret config |
| Observability | **OpenTelemetry → Grafana Cloud** (ADR-0008) | AWS managed (AMP, AMG, X-Ray, CloudWatch), self-hosted LGTM, Datadog | Vendor-neutral, free tier, identical local stack (`grafana/otel-lgtm`) | Egress through NAT; data leaves AWS |
| Local dev | Docker Compose: `pgvector/pgvector:pg16`, `valkey/valkey`, LocalStack (SNS/SQS), Ollama, `grafana/otel-lgtm` | Remote dev env | Whole system on a laptop | Memory-hungry; use compose profiles to start only what you need |

## Testing

| Level | Tool | Used for |
|---|---|---|
| Unit | JUnit 5 + Mockito (`@ExtendWith(MockitoExtension.class)`) + AssertJ | Services, strategies, mappers (existing convention) |
| Architecture | **ArchUnit** | Domain must not depend on Spring/JPA/AWS; services depend on ports only |
| Integration | **Testcontainers** (pgvector, Valkey, LocalStack) + `@SpringBootTest` | Repositories, controllers, WS multi-node, outbox → SQS |
| Contract | OpenAPI diff in CI | Frontend/backend drift |
| E2E | Playwright | Login → send → receive across two browsers |
| Load | **k6** (WebSocket module) | Concurrent sockets per task, p99 delivery latency, reconnect storm |
| RAG quality | JUnit `@Tag("eval")` with a golden set | Recall@k, citation accuracy, groundedness |
