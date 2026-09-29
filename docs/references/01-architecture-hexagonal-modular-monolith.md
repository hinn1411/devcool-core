# 01 — Hexagonal architecture and the modular monolith

> **Used in DevCool:** [ADR-0001](../plans/architecture/adr/0001-modular-monolith-hexagonal.md) (api + worker from one image) · [ADR-0004](../plans/architecture/adr/0004-monorepo.md) (monorepo) · [01 §3 and §5.1](../plans/architecture/01-system-architecture.md#51-modular-monolith-api--worker-from-one-image-vs-microservices) · P1-T13 (ArchUnit) · P6-T04 (worker profile)
> **Already in the repo:** [learning/03 — Hexagonal architecture](../learning/03-hexagonal-architecture.md) · [architecture-request-flow.md](../architecture-request-flow.md) · [.claude/rules/hexagonal.md](../../.claude/rules/hexagonal.md)
> **Links checked:** 2026-09-29

## Concepts to own

- **Ports and adapters.** The application core talks to the outside only through interfaces it owns. *Inbound* (driving) ports are the use cases that controllers, WebSocket handlers and SQS listeners call. *Outbound* (driven) ports are what the core needs from the world (DB, S3, Redis, Bedrock), and adapters implement them. *In DevCool:* `domain/*/port/in` and `domain/*/port/out`.
- **The dependency rule.** Source dependencies point inward: `adapters → application → domain`. The domain never imports Spring, JPA or the AWS SDK. *In DevCool:* enforced by ArchUnit in P1-T13, not only by review.
- **Transport-free commands.** A port's parameter types are its contract. A `MultipartFile` in a domain command ties the use case to HTTP. *In DevCool:* the `UploadMediaCommand` leak in learning/03.
- **Modular monolith.** One deployable with enforced internal module boundaries. You get most of the design benefits of services without distributed transactions, N pipelines or service discovery.
- **Split by runtime profile, not by noun.** Latency-sensitive work (REST, WS, RAG streaming) and throughput work (outbox relay, SQS consumers, embedding) scale on different signals. The same image runs as two ECS services chosen by Spring profile. *In DevCool:* `ecs,api` and `ecs,worker`.
- **Profile-guarded beans.** `@Profile` and `@ConditionalOnProperty` switch schedulers and listeners on or off. Forget one and every `api` task runs the outbox relay.
- **Architecture fitness functions.** Tests that fail the build when a boundary is crossed (ArchUnit, Spring Modulith's `verify()`).
- **Architecture Decision Records.** A short, immutable record of one decision: context, options, decision, consequences. *In DevCool:* `docs/plans/architecture/adr/`.
- **Monorepo.** One repository for backend, frontend and infra, so one PR can change a contract on both sides. CI uses path filters to stay fast.

## Read first

1. [Hexagonal Architecture](https://alistair.cockburn.us/hexagonal-architecture/) — *original article, Alistair Cockburn* · The intent (testability, swapping drivers) in the author's words. Read the "ports" vs "adapters" naming discussion.
2. [Hexagonal Architecture with Java and Spring](https://reflectoring.io/spring-hexagonal/) — *Tom Hombergs* · The closest match to DevCool's package layout: use case interfaces, commands, persistence adapters.
3. [ArchUnit User Guide](https://www.archunit.org/userguide/html/000_Index.html) — *official docs* · Sections "Layer Checks" and "Architectures" (`layeredArchitecture()`, `onionArchitecture()`). This is what P1-T13 writes.
4. [MonolithFirst](https://martinfowler.com/bliki/MonolithFirst.html) — *Martin Fowler* · The argument behind ADR-0001, in one page.
5. [Documenting Architecture Decisions](https://cognitect.com/blog/2011/11/15/documenting-architecture-decisions) — *Michael Nygard* · Where the ADR format comes from.

## Reference

### Hexagonal, onion and clean architecture

- [Ready for changes with Hexagonal Architecture](https://netflixtechblog.com/ready-for-changes-with-hexagonal-architecture-b315ec967749) — *Netflix Tech Blog* · A production case: swapping a data source without touching business logic.
- [DDD, Hexagonal, Onion, Clean, CQRS… how I put it all together](https://herbertograca.com/2017/11/16/explicit-architecture-01-ddd-hexagonal-onion-clean-cqrs-how-i-put-it-all-together/) — *Herberto Graça* · How the named styles relate; good for "what's the difference between hexagonal and clean?".
- [The Clean Architecture](https://blog.cleancoder.com/uncle-bob/2012/08/13/the-clean-architecture.html) — *Robert C. Martin* · The dependency rule stated precisely.

### Modular monolith vs microservices

- [Microservice Premium](https://martinfowler.com/bliki/MicroservicePremium.html) — *Martin Fowler* · Why microservices cost more than they return for small teams.
- [Microservices](https://martinfowler.com/articles/microservices.html) — *Lewis & Fowler* · The defining article; read it to argue the other side.
- [Deconstructing the Monolith](https://shopify.engineering/deconstructing-monolith-designing-software-maximizes-developer-productivity) — *Shopify Engineering* · A large company choosing a modular monolith, and how they enforce boundaries.
- [Modular Monolith: A Primer](https://www.kamilgrzybek.com/blog/posts/modular-monolith-primer) — *Kamil Grzybek* · Definitions and module coupling rules.
- [Spring Modulith reference](https://docs.spring.io/spring-modulith/reference/1.4/) — *official docs* · An alternative to hand-written ArchUnit rules: module verification, documentation, and an event publication registry (a built-in outbox; compare with [06](06-event-driven-outbox-sns-sqs.md)).

### Spring wiring for one image, two services

- [Spring Boot — Profiles](https://docs.spring.io/spring-boot/3.5/reference/features/profiles.html) — *official docs* · Profile groups and `spring.profiles.active`, which select `api` vs `worker`.
- [Spring Framework — Environment abstraction and `@Profile`](https://docs.spring.io/spring-framework/reference/6.2/core/beans/environment.html) — *official docs* · How `@Profile` works on beans and configuration classes.
- [Spring Boot — Condition annotations](https://docs.spring.io/spring-boot/3.5/reference/features/developing-auto-configuration.html#features.developing-auto-configuration.condition-annotations) — *official docs* · `@ConditionalOnProperty` and friends, for switching listeners and schedulers.

### ADRs and monorepos

- [Architectural Decision Records (adr.github.io)](https://adr.github.io/) — *community hub* · Templates (MADR, Nygard) and tooling.
- [monorepo.tools](https://monorepo.tools/) — *overview site* · Monorepo trade-offs and tooling vocabulary for ADR-0004.

### Books

- *Get Your Hands Dirty on Clean Architecture*, 2nd ed. (Tom Hombergs) — the whole book maps onto DevCool's layout; chapters on use cases, persistence adapters and enforcing boundaries.
- *Monolith to Microservices* (Sam Newman) — ch. 1–3 for when and how to extract a service (the "revisit when" of ADR-0001).
- See [17 — Books and courses](17-books-and-courses.md).

## Self-check

- What is the difference between an inbound port and an outbound port? Name one of each in DevCool.
- Why does a `MultipartFile` in a domain command break the architecture, even though it compiles?
- Which ArchUnit rule would catch an application service that injects a Spring Data repository directly?
- Why split `api` and `worker` by runtime profile rather than into chat/presence/AI services?
- What goes wrong if an `@Scheduled` outbox relay is not profile-guarded?
- When would you extract a real microservice from this monolith? What makes the extraction mechanical?
- What belongs in an ADR, and why are ADRs never edited after acceptance?
