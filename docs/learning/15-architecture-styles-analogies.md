# 15 — Modular Monolith, Microservices, Distributed Monolith

The theme: **how many things do you deploy, and are the boundaries between the parts real?** This is the background for [ADR-0001](../plans/architecture/adr/0001-modular-monolith-hexagonal.md) (one image, `api` + `worker`) and [ADR-0007](../plans/architecture/adr/0007-event-backbone-outbox-sns-sqs.md) (outbox + SNS/SQS).

| | Deployables | Boundaries |
|---|---|---|
| **Modular monolith** | One | Strong, enforced in code |
| **Microservices** | Many | Strong, enforced by the network and owned data |
| **Distributed monolith** | Many | Weak: the services are tightly coupled |

The whole chapter uses one analogy: **three ways to run a restaurant.**

---

## 1. Modular monolith: one restaurant with kitchen stations

**The concept.** One codebase and one deployable, split internally into modules (`chat`, `channel`, `member`, `media`, …) that talk only through defined interfaces (ports). A tool checks the rules: here, ArchUnit and the hexagonal layout. `api` + `worker` from one image still counts as one: it's one artifact run in two roles.

**The analogy.** One building, one kitchen, one head chef, one opening time. Inside, the kitchen is split into **stations**: grill, salad, pastry. Each station has its own counter and tools. Orders pass between them through a **pass window** with tickets (the ports). The grill cook never walks over and grabs the pastry chef's butter.

| | Analogy | In software |
|---|---|---|
| 👍 | Everyone is a few steps apart: an order handoff is instant and reliable | In-process calls: fast, no network failures, simple stack traces |
| 👍 | One fridge, one inventory, so stock is always consistent | One DB transaction can cover several modules, e.g. save a message and write its outbox row atomically |
| 👍 | One rent, one license, one crew to manage | One build, one CI pipeline, one schema; a rename across modules is one IDE refactor |
| 👍 | A station can later become its own stall: the pass window already exists | You can extract a service later: the ports are already the seams |
| 👎 | A gas leak closes *everything* | One deploy, shared failure |
| 👎 | Renovating the pastry station closes the whole restaurant for the day | A bug fix in `media` redeploys `chat` too |
| 👎 | Without a strict head chef, cooks wander into each other's stations, and soon it's chaos | Boundaries erode into a "big ball of mud" unless a tool enforces them. **ArchUnit is the strict head chef.** |
| 👎 | One kind of oven for everyone | One language and runtime |

**In DevCool.** `api` and `worker` are the **dining-room crew** and the **prep crew working overnight**. Same restaurant, same recipes, different shifts and staffing levels. Prep (embeddings, the outbox relay, notifications) never slows down serving (WebSocket delivery), and each crew is staffed on its own signal: connections for `api`, SQS queue depth for `worker`.

---

## 2. Microservices: a food court

**The concept.** Each capability is a separate service with its **own database**, its own deploy pipeline and often its own team. Services talk over HTTP/gRPC or events. For DevCool that would be `chat-service`, `presence-service`, `ai-service` and `notification-service`, each with its own DB.

**The analogy.** Independent **stalls**: Burger, Sushi, Drinks. Each has its **own kitchen, own fridge, own staff, own opening hours**. They don't share ingredients. To combine things, they send messages: "table 5 wants a drink with their burger."

| | Analogy | In software |
|---|---|---|
| 👍 | Sushi can renovate while Burger stays open | Independent deploys |
| 👍 | Lunch rush on burgers? Hire more burger staff only | Independent scaling, e.g. `ai-service` on GPU nodes alone |
| 👍 | If Drinks burns down, you can still eat | Failure isolation |
| 👍 | Each stall picks its own cuisine and equipment | Each service can use a different language or tech |
| 👍 | Each stall has its own owner | Teams own services end to end, which suits large organizations |
| 👎 | A combo meal needs coordination, and sometimes the drink arrives without the burger | No transaction across services: sagas, outbox, eventual consistency |
| 👎 | Messengers walk between stalls and sometimes get lost | Network failures: timeouts, retries, circuit breakers, idempotency everywhere |
| 👎 | N leases, N health inspections, N managers | N pipelines, N dashboards, service discovery, distributed tracing, API versioning |
| 👎 | For **one cook**, running five stalls means running between them all day | It solves *organizational* scaling. For one developer it's mostly overhead, which is why ADR-0001 rejected it |

---

## 3. Distributed monolith: a food court that's secretly one kitchen

**The concept.** It *looks* like microservices but behaves like a monolith, so you pay the costs of both and get the benefits of neither. It's an anti-pattern. It usually comes from splitting a monolith by *technical* layer (a "DB service", a "validation service") instead of by business capability, or from splitting before the boundaries are known.

**The analogy.** Separate stalls and signs out front, but:

| Analogy | Symptom in software |
|---|---|
| All stalls **share one fridge in the back**. When Sushi rearranges the shelves, Burger can't find the cheese | **Shared database.** `chat-service` and `notification-service` both write the `message` table, so a schema change breaks both |
| Burger can't open until Drinks opens, and Drinks waits on Fries | **Lockstep deploys.** You can't release one service without the others, because they share a DTO library or depend on each other's internals |
| To make one burger, the cook **phones** the bun stall, which phones the meat stall, which phones the sauce stall, and the customer waits on all three calls. One dead line, no burger | **Synchronous call chains.** Sending a message calls `member` → `presence` → `notification` over HTTP in the request path. Any one down fails the send, and latency adds up at every hop |
| The cook makes 20 phone calls for one order | **Chatty calls.** Boundaries drawn in the wrong place |

You pay for separate leases, managers and messengers (microservice costs), and you *still* can't renovate one stall without closing the others (monolith coupling).

---

## 4. Two ideas to keep

**A ticket rail, not a phone call.** The outbox + SNS/SQS ([ADR-0007](../plans/architecture/adr/0007-event-backbone-outbox-sns-sqs.md)) is a **ticket rail**. The dining room (`api`) pins a ticket ("message created") in the same transaction as the message and moves on. Prep (`worker`) picks it up when ready. Nobody waits, and a ticket isn't lost if prep is on break: the backlog drains when the worker restarts (P9-T06). This is a microservice technique used *inside* the monolith, and it's what keeps a slow embedding from blocking a send.

**Stations first, stalls later.** Start as one restaurant with clean stations. If pastry ever becomes huge and needs a different oven (a Python ML service, ADR-0001's "Revisit when"), move *that one station* into its own stall. Its pass window (port) already exists, so the move is mechanical. Splitting into stalls before you know where the stations are is how you end up with the shared-fridge mess.

---

## Where else this applies

- **Multi-module Maven** ([ADR-0004](../plans/architecture/adr/0004-monorepo.md) "Revisit when"): splitting into Maven modules is putting walls between stations. It's still one restaurant, but the compiler, not just ArchUnit, stops cooks from crossing over.
- **A second language for the worker:** that's giving the prep crew a different recipe book while they still share the fridge (schema) and the house rules (membership, RAG access control). That's the shared-fridge problem again, which is why the worker stays in Java.
- **Any new `@Scheduled` job or SQS listener:** it belongs to the prep crew. Guard it with the `worker` profile, or every `api` task will run it too (ADR-0001 consequences).
