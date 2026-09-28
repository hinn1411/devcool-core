# 0007 — Event backbone: transactional outbox → SNS → SQS

- **Status:** Accepted
- **Date:** 2026-09-29
- **Phase:** P6 (used by P8)

## Context
Several things must happen *because* a message was created, edited or deleted, but must not slow down or fail the send:
- **Indexer:** re-embed the affected chunk (P8).
- **Notifier:** unread badges, notifications for users with no live socket, later email/web push.
- **Future:** auto-tagging, analytics, audit.

This is a different problem from realtime fan-out (ADR-0006):
- **Fan-out** is lossy, sub-100 ms and ephemeral.
- **Events** must be **durable and at-least-once**, can take seconds, and have independent consumers that fail and retry on their own.

The hard part is the **dual write**: saving the message in Postgres and publishing an event are two systems. Without care, one can succeed while the other fails.

## Decision drivers
1. No lost events and no phantom events (event published for a rolled-back transaction).
2. Independent consumers with their own retry and dead-letter handling.
3. Scale to zero when idle; low ops burden.
4. Works locally and in integration tests.
5. Ordering: needed per aggregate, but can be handled by consumers.
6. Interview value.

## Options considered

### Option 1 — Direct publish after commit (no outbox)
```java
@Transactional save(msg);    // commit
snsClient.publish(event);    // separate system
```
- Pros: simplest code.
- Cons: **dual-write problem.** If the process crashes after the commit and before the publish, the event is lost forever. If you publish inside the transaction and the transaction rolls back, you get a phantom event. Retrying the publish after commit needs its own durable store, which is an outbox anyway.
- Verdict: **anti-pattern.** Shown here because interviewers ask "why not just publish?"

### Option 2 — Transactional outbox + relay → SNS → SQS (**chosen**)
```
tx: INSERT message …; INSERT outbox(id, aggregate_type, aggregate_id, type, payload, created_at)
relay (worker): SELECT … FROM outbox WHERE published_at IS NULL
                ORDER BY id LIMIT 100 FOR UPDATE SKIP LOCKED
                → SNS PublishBatch (with MessageAttributes: type, traceparent)
                → UPDATE outbox SET published_at = now()
SNS topic devcool-events → subscription filter policy per consumer → SQS queue (+ DLQ)
```
- **Pros:**
  - Atomic: the event exists iff the transaction committed.
  - `SKIP LOCKED` lets several worker tasks relay in parallel without double-publishing the same row.
  - SNS gives fan-out to N queues with filter policies.
  - SQS gives per-consumer buffering, visibility timeout retries, a DLQ after `maxReceiveCount`, and redrive.
  - Fully managed; costs nothing idle.
  - LocalStack emulates both for local dev and Testcontainers ITs.
- **Cons:**
  - **At-least-once:** the relay can publish and crash before marking the row, so it publishes again. Consumers **must be idempotent** (a `processed_event` table + aggregate `version`).
  - Standard SNS/SQS **don't preserve order.** Consumers either tolerate reordering (re-read current state from the DB, compare `version`) or you use FIFO topics/queues with `MessageGroupId = channelId` (ordered per channel; throughput limits apply).
  - Polling latency: 100 ms–1 s between commit and publish. Fine for background work.
  - No replay beyond SQS retention (max 14 days). Re-drive from the outbox table if you keep rows.
  - Outbox table growth: delete or partition rows older than N days.

### Option 3 — Outbox → Kafka (Amazon MSK / MSK Serverless)
- **Pros:**
  - Ordered partitions (key = `channelId`).
  - Long retention and **replay** from any offset (e.g. re-index from the log).
  - High throughput.
  - The strongest "event streaming" keyword on a CV.
- **Cons:**
  - A cluster to size, secure (IAM/SASL), monitor and upgrade.
  - MSK Serverless has a per-cluster-hour charge, so it **doesn't scale to zero**.
  - Consumer group rebalancing, offset management and partition count planning add complexity.
  - The Java client is heavier; local dev needs a Kafka container.
  - At this scale it's all capability you don't use.

### Option 4 — Outbox → Amazon EventBridge
- **Pros:**
  - Content-based routing rules.
  - Schema registry, archive and **replay** built in.
  - Many AWS targets (Lambda, Step Functions, SQS).
  - Scales to zero.
- **Cons:**
  - Higher per-event latency (often 100s of ms) and a per-event price.
  - Targets still need SQS in front of the workers for buffering and DLQs, so it adds a hop.
  - Less common in interview "chat system" answers.

### Option 5 — Redis Streams (on the Valkey we already run)
- **Pros:**
  - No new service.
  - Consumer groups with pending-entry lists (`XREADGROUP`, `XACK`, `XCLAIM`).
  - Ordered per stream.
  - Very low latency.
- **Cons:**
  - Durability depends on Redis persistence settings. ElastiCache Serverless is a cache: plan for data loss on failover.
  - DLQ, redrive and retry policies are all yours to build.
  - It still needs an outbox to avoid the dual write with Postgres.

### Option 6 — Change data capture (Debezium reading the Postgres WAL)
- **Pros:**
  - No relay code.
  - Captures every change even from code paths that forget to write events.
  - Ordered per table.
- **Cons:**
  - Runs Kafka Connect (so Kafka too), and logical replication slots on Aurora can bloat WAL if the connector stops.
  - Events are row-shaped, not domain-shaped, unless you use the "outbox event router" pattern, which needs the outbox table anyway.
  - Heaviest ops.

## Comparison

| Criterion | 1 Direct | **2 Outbox+SNS/SQS** | 3 Kafka | 4 EventBridge | 5 Redis Streams | 6 Debezium |
|---|---|---|---|---|---|---|
| No lost / phantom events | ✗ | ✓ | ✓ (with outbox) | ✓ (with outbox) | ✓ (with outbox) | ✓ |
| Delivery | at-most-once | at-least-once | at-least-once | at-least-once | at-least-once | at-least-once |
| Ordering | — | per group with FIFO; else via `version` | per partition | none | per stream | per table/partition |
| Replay | ✗ | outbox table / 14 d | ✓ (retention) | ✓ (archive) | ✓ (stream length) | ✓ (Kafka) |
| DLQ / retry built in | ✗ | ✓ | build it | ✓ (to SQS) | build it | build it |
| Scale to zero | ✓ | ✓ | ✗ | ✓ | ✗ (Valkey min) | ✗ |
| Ops burden | none | low | high | low | low–medium | high |
| Local / IT | trivial | LocalStack | Kafka container | LocalStack (partial) | Valkey container | Kafka + Connect |
| Latency | ms | 0.1–1 s | ms–100 ms | 100s ms | ms | 100 ms–s |
| Interview value | "what not to do" | **high: the classic pattern** | high | medium | medium | high, but heavy |

## Decision
**Option 2**, with standard (non-FIFO) SNS and SQS:
- One SNS topic `devcool-events`.
- One SQS queue per consumer (`indexer`, `notifier`), each with a DLQ (`maxReceiveCount = 5`) and an alarm on DLQ depth > 0.
- Subscription **filter policies** on the `type` message attribute, so each queue only receives what it handles.
- Consumers are idempotent (a `processed_event(event_id, consumer)` table, in the same transaction as their effect) and order-tolerant (they re-read current state and guard with `version`).
- The relay runs in the `worker` service, polling every 200 ms with `FOR UPDATE SKIP LOCKED` and batch publishes. Published rows are deleted after 7 days by a daily job.
- The W3C `traceparent` is copied into SNS message attributes so traces continue into consumers (ADR-0008).

Event envelope:
```json
{ "eventId": "uuid", "type": "MessageEdited", "occurredAt": "2026-…Z",
  "aggregateType": "Message", "aggregateId": 812, "aggregateVersion": 3,
  "channelId": 12, "payload": { } }
```

## Consequences
- Every event consumer needs an idempotency test: deliver the same event twice and assert one effect.
- Every consumer needs an ordering test: deliver v3 then v2 and assert the final state is v3.
- Outbox lag (oldest unpublished `created_at`) is a key metric and alert (P7).
- IAM: `worker` may `sns:Publish` to one topic and `sqs:ReceiveMessage/DeleteMessage` on its queues only.

## Revisit when
- You need replay of weeks of history, or > ~1k events/s sustained → Kafka (Option 3). The outbox stays; only the relay target changes.
- A consumer needs strict per-channel order and can't be made order-tolerant → FIFO topic + queues with `MessageGroupId = channelId`.
