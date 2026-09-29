# 17 — Books, courses and certifications

> **Used in DevCool:** cross-cutting. Each topic file lists the chapters that matter for its topic; this is the consolidated list.
> **Links checked:** 2026-09-29. Some publisher sites (O'Reilly) block automated checks; those books are listed without a link.

## How to use this list

You don't need to read these cover to cover. Each row names the parts that pay off for this roadmap. A reasonable order is the "Core" table top to bottom, alongside the phases.

## Core (read during the roadmap)

| Book | Read | Why for DevCool | Topic files |
|---|---|---|---|
| [*Designing Data-Intensive Applications*, 2nd ed.](https://martin.kleppmann.com/2026/03/24/designing-data-intensive-applications-2e.html) — Kleppmann & Riccomini, O'Reilly 2026 ([book site](https://dataintensive.net/)) | Chapters on transactions, replication, the trouble with distributed systems, stream processing | Isolation and locking (seq, outbox), clocks and ordering, delivery guarantees, CDC vs outbox | [02](02-chat-system-design.md), [05](05-postgresql-and-data-access.md), [06](06-event-driven-outbox-sns-sqs.md) |
| [*System Design Interview — An Insider's Guide*, vol. 1](https://bytebytego.com/courses/system-design-interview) — Alex Xu | Back-of-envelope, rate limiter, unique id generator, chat system | The interview framing of exactly what DevCool builds | [02](02-chat-system-design.md), [04](04-redis-valkey.md) |
| [*Get Your Hands Dirty on Clean Architecture*, 2nd ed.](https://reflectoring.io/book/) — Tom Hombergs | All (short) | Hexagonal architecture in Java/Spring with the same package vocabulary | [01](01-architecture-hexagonal-modular-monolith.md) |
| [*Microservices Patterns*](https://microservices.io/book) — Chris Richardson ([2nd ed. at Manning](https://www.manning.com/books/microservices-patterns-second-edition)) | Transactional messaging (outbox), sagas, testing | The outbox/idempotent-consumer patterns in their original context | [06](06-event-driven-outbox-sns-sqs.md) |
| [*Release It!*, 2nd ed.](https://pragprog.com/titles/mnee2/release-it-second-edition/) — Michael Nygard | Part I (stability anti-patterns and patterns) | Timeouts, circuit breakers, bulkheads, steady state: the Bedrock and Valkey failure stories | [10](10-spring-boot-and-java-runtime.md) |
| [*Site Reliability Engineering*](https://sre.google/books/) and *The Site Reliability Workbook* — Google (free online) | Book ch. 4 (SLOs), ch. 6 (monitoring); Workbook "Implementing SLOs", "Alerting on SLOs" | SLOs and burn-rate alerts in P7 | [12](12-observability.md) |
| [*Terraform: Up & Running*, 3rd ed.](https://www.terraformupandrunning.com/) — Yevgeniy Brikman | State, modules, team workflow chapters | Layered stacks and state isolation (ADR-0003) | [08](08-terraform-iac.md) |
| [*High Performance Browser Networking*](https://hpbn.co/) — Ilya Grigorik (free online) | WebSocket, SSE, TLS chapters | What happens under the WS client and CloudFront | [03](03-websocket-realtime-protocol.md) |
| *AI Engineering* — Chip Huyen, O'Reilly 2025 ([companion repo](https://github.com/chiphuyen/aie-book)) | Evaluation, RAG, inference optimization chapters | The evaluation harness and RAG trade-offs in P8 | [13](13-genai-rag-bedrock.md) |

## Deepen (after the roadmap, or when a topic becomes your bottleneck)

| Book | Why |
|---|---|
| [*High-Performance Java Persistence*](https://vladmihalcea.com/books/high-performance-java-persistence/) — Vlad Mihalcea | JDBC, connection pools, JPA fetching and locking in depth ([05](05-postgresql-and-data-access.md)) |
| [*Database Internals*](https://www.databass.dev/) — Alex Petrov | Storage engines and distributed consensus; the "why" under Postgres and Cassandra |
| *Observability Engineering* — Majors, Fong-Jones & Miranda, O'Reilly 2022 | High-cardinality events, SLO-based alerting, observability-driven development ([12](12-observability.md)) |
| [*Building Microservices*, 2nd ed.](https://samnewman.io/books/building_microservices_2nd_edition/) — Sam Newman | The other side of ADR-0001: when services are worth it |
| [*Monolith to Microservices*](https://samnewman.io/books/monolith-to-microservices/) — Sam Newman | How to extract a service from a modular monolith when the time comes |
| [*Enterprise Integration Patterns*](https://www.enterpriseintegrationpatterns.com/) — Hohpe & Woolf | The messaging pattern language (idempotent receiver, dead letter channel, …) |
| *Learning Domain-Driven Design* — Vlad Khononov, O'Reilly 2021 | Bounded contexts and aggregates; useful when module boundaries get contested |
| [*Unit Testing Principles, Practices, and Patterns*](https://www.manning.com/books/unit-testing) — Vladimir Khorikov | The testing schools and what to mock ([15](15-testing-and-load.md)) |

## Courses and lectures (free)

- [Distributed Systems lecture series](https://www.youtube.com/playlist?list=PLeKd45zvjcDFUEv_ohr_HdUFe97RItdiB) — *Martin Kleppmann, Cambridge* · Eight short lectures; [lecture notes (PDF)](https://www.cl.cam.ac.uk/teaching/2122/ConcDisSys/dist-sys-notes.pdf). The best free companion to DDIA.
- [MIT 6.5840 Distributed Systems](https://pdos.csail.mit.edu/6.824/) — *MIT* · Papers + labs (Raft, sharded KV store). Heavy; take it only if distributed systems becomes your focus.
- [Anthropic courses](https://github.com/anthropics/courses) — *Anthropic* · Prompt engineering and tool-use tutorials as notebooks; useful before writing the RAG prompt in P8.
- [PortSwigger Web Security Academy](https://portswigger.net/web-security/access-control) — *free labs* · Access control, JWT and WebSocket labs ([11](11-security.md)).

## Certifications that line up with the roadmap

| Certification | Exam guide | Overlap |
|---|---|---|
| AWS Certified Generative AI Developer – Professional (AIP-C01) | [Exam guide](https://docs.aws.amazon.com/aws-certification/latest/ai-professional-01/ai-professional-01.html) | P8: Bedrock, RAG, vector stores, guardrails, evaluation. Mentioned in ADR-0010 |
| AWS Certified Solutions Architect – Associate (SAA-C03) | [Exam guide](https://docs.aws.amazon.com/aws-certification/latest/solutions-architect-associate-03/solutions-architect-associate-03.html) | P2: VPC, ECS, ALB, CloudFront, Aurora, SQS/SNS, IAM |
| HashiCorp Terraform Associate | [Certification page](https://developer.hashicorp.com/certifications/infrastructure-automation) | P2: state, modules, workflow |
