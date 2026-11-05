# §8 Interview readiness

[← Playbook](../delivery-playbook.md)

## Two tracks

| Track | Gate | Kit |
|---|---|---|
| **Backend** (apply from week 6) | P1 + P3 + P4 done | 30-second pitch; 5-minute whiteboard of send, fan-out and resume; 5 STAR stories; the backend and cloud questions below, out loud |
| **AI** (from week 11) | P8a–d done | 30-second RAG pitch; leak-test story; eval numbers before/after a tuning change; injection defence; cost control |

**Backend STAR candidates:** the BOLA and password-hash leak fix (P1-T04, [learning/02](../../learning/02-security-and-authorization.md)) · the seq race and idempotent send (P3-T03) · an N+1 or missing index (week-4 game day) · baselining a live schema into Flyway (P1-T01) · the drain or Valkey-outage game day.

**AI STAR candidates:** the leak test and permission-aware retrieval · an eval-driven tuning change · the injection test · the circuit-breaker fallback.

**Cross-cutting:** the $20 budget (ADR-0013): a real constraint, and the trade-offs it forced.

## Weekly drill (Fridays, 1 hour)

1. `/interview-prep` on the phase you finished; edit `docs/plans/interview/<topic>.md` until every answer is yours.
2. A "grill me" session ([prompt](03-task-loop.md#prompts)) on the week's topic.
3. Record one 2-minute answer and listen back: did you name the trade-off and a number?
4. Add every missed question to your list, with the file to read.

## Question bank

Answers come from your own work. These add to [03 §13](../architecture/03-chat-system-design.md#13-interview-checklist), the reference Self-checks, and the [Claude Code guide Q&A](../claude-code/claude-code-guide.md#11-interview-qa).

**Backend system design**

| # | Question | Material | A good answer includes |
|---|---|---|---|
| 1 | Design a chat system for 10k DAU. Walk me through a send | [03](../architecture/03-chat-system-design.md), [01 §4.1](../architecture/01-system-architecture.md#41-send-a-message-hot-path) | Estimation first; commit before ACK; local + backplane delivery |
| 2 | How do you guarantee ordering? Why not timestamps? | ADR-0012 | Per-channel scope; the row lock; gap detection |
| 3 | How do you stop duplicates when clients retry? | P3-T03 | Unique key; same seq returned; no second bump |
| 4 | Users on different servers: how do they get each other's messages? | ADR-0006 | Per-channel topics; at-most-once plus resume |
| 5 | What happens to connected users during a deploy? | 01 §4.5, week-6 game day | Readiness, `RECONNECT` with jitter, 1012; measured reconnect spread |
| 6 | How does a client know it missed something? | P4-T09 | Seq gap → `RESUME`; the 200 cap → `RESYNC_REQUIRED` |
| 7 | What breaks first at 100× load? | [01 §7](../architecture/01-system-architecture.md#7-scaling-path-beyond-this-roadmap) | Registry/fan-out before the DB; partitioning; gateway tier |
| 8 | Why a modular monolith? | ADR-0001 | Split by runtime profile; ports make extraction mechanical |

**Backend deep dive**

| # | Question | Material | A good answer includes |
|---|---|---|---|
| 9 | Walk through the send transaction. Which locks? | [ref 05](../../references/05-postgresql-and-data-access.md) | `UPDATE … RETURNING` row lock; Read Committed re-check; outbox in the same tx |
| 10 | Tell me about an N+1 you found | [learning/01](../../learning/01-jpa-and-transactions.md), week-4 game day | How you detected it (query count) and the measured fix |
| 11 | How did you move an existing schema to Flyway? | P1-T01 | Baseline, `validate`, never editing merged migrations |
| 12 | Authentication vs authorization, with a bug you fixed | P1-T04, T06 | The BOLA example; service-layer checks; negative tests |
| 13 | Optimistic vs pessimistic locking: where? | P3-T03, P3-T06 | Channel row lock for seq; `version` for edits → 409 |
| 14 | How do you test concurrency? | [testing rules](../../../.claude/rules/testing.md) | Latch, ≥ 20 iterations, assert invariants not timings |
| 15 | Why an outbox and not publish-after-commit? | ADR-0007 | Dual write; the crash window; at-least-once |
| 16 | How are your consumers idempotent and order-tolerant? | P6-T07 | `processed_event` in the same tx; re-read + `version` guard |
| 17 | What did virtual threads change, and the risk? | P1-T16, [ref 10](../../references/10-spring-boot-and-java-runtime.md) | Cheap blocking; pinning in `synchronized` on Java 21 |
| 18 | What happens with a slow WebSocket client? | P4-T01 | Buffer/time limit; terminate; resume |

**Cloud**

| # | Question | Material | A good answer includes |
|---|---|---|---|
| 19 | Why ECS Fargate and not EKS or Lambda? | ADR-0002 | Ops burden vs portability; WS support; sidecars |
| 20 | Trace a request from the browser to your container | [ref 07](../../references/07-aws-compute-edge-networking.md) | CloudFront behaviours → ALB → task; single origin |
| 21 | How does CI deploy without AWS keys? | P2-T01 | OIDC trust policy pinned to repo/branch |
| 22 | How did you keep the bill under $20? | ADR-0013, [§6](06-aws-budget.md) | Hourly-billed resources; hibernate; trade-offs accepted |
| 23 | How do you run migrations with N tasks? | P2-T05, T08 | A one-off migrate task before the service update |
| 24 | What do you alert on? | P7-T08, T09 | SLOs and burn rate; outbox lag; DLQ depth; low cardinality |

**AI**

| # | Question | Material | A good answer includes |
|---|---|---|---|
| 25 | Explain your RAG pipeline | [04](../architecture/04-genai-rag-design.md) | Event-driven indexing; the send path never waits on an LLM |
| 26 | How can't a user retrieve a private channel? | P8-T09, T10 | Filter *inside* the vector query; the leak test |
| 27 | How do you chunk chat? | P8-T02 | Conversation windows; overlap; replies |
| 28 | How do you know answers are good? | P8-T19 | recall@8, citation precision, refusal accuracy, groundedness |
| 29 | How do you defend against prompt injection? | P8-T13, T20 | Delimited sources, no tools, citation validation |
| 30 | What happens when Bedrock is slow or down? | P8-T16 | Time limits; retry before the first token only; fallback |
| 31 | How do you control LLM cost? | P8-T17, [§6](06-aws-budget.md) | Rate limit, token budget, model tiering, metrics |

**Behavioural and AI use**

| # | Question | Material |
|---|---|---|
| 32 | A bug that got past your first tests | Journal incidents; "Claude got wrong" log |
| 33 | A trade-off under a hard constraint | ADR-0013; the cut list |
| 34 | Something that went wrong in a drill, and what you changed | Incident reports |
| 35 | How do you use AI coding tools without lowering quality? | [§2](02-ownership.md), [§3](03-task-loop.md), [§7](07-claude-code.md) |
| 36 | What did the AI get wrong, and how did you catch it? | Journal log |
| 37 | How did you decide what to cut? | [§4](04-pace-and-scope.md) cut list; weekly reviews |
| 38 | How do you learn a new technology quickly? | [§3](03-task-loop.md); reference files |
| 39 | What would you do differently? | Weekly reviews |

## Honesty rules

- **Built, planned or simulated:** say which. "In a game day against two local instances…" is strong; "In production…" falls apart at the first follow-up.
- **You vs Claude:** say what you designed and wrote (A/B) and what you delegated and reviewed (C).
- **Numbers only from your own runs,** with the conditions ("p99 on a 0.5 vCPU task, 2,000 sockets, k6 chat mix").

## CV bullet formula

`<verb> <what> with <how>, <result with a number> (<how it was measured>)`

*"Designed per-channel sequencing and idempotent send for a Spring Boot WebSocket chat backend, with zero lost or duplicated messages across [N] kill-node and reconnect-storm drills at [X] concurrent sockets (k6, seq audit against the DB)."*
