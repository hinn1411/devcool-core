# Phase 6 — Async events: outbox → SNS → SQS

**Week:** 6 · **Depends on:** P3 (message versions), P2 (infra) · **ADRs:** 0001, 0007

## Goal
A durable, at-least-once event pipeline, decoupled from the request path:
- Transactional outbox.
- A relay in a new `worker` ECS service.
- SNS topic with filtered SQS queues and DLQs.
- Idempotent, order-tolerant consumers.

The first consumer is `notifier` (unread/notifications, S3 media cleanup). P8 adds `indexer`.

## Why it matters (interview angle)
The dual-write problem and the outbox pattern come up in almost every backend interview that touches microservices or events. You'll have a running, tested implementation, including the failure cases.

## Scope
- **In:**
  - Outbox table and writer.
  - Relay.
  - `worker` service.
  - SNS/SQS/DLQ in Terraform.
  - Consumer base.
  - `notifier` consumer.
  - LocalStack for local and IT.
  - Trace propagation.
- **Out:** email/web push delivery (a stub interface only), Kafka.

## Design notes
- The envelope and relay algorithm are in [ADR-0007](../architecture/adr/0007-event-backbone-outbox-sns-sqs.md#decision).
- **Outbox writer:** `OutboxPort.append(DomainEvent)` is called by application services inside their existing `@Transactional` method (e.g. `MessageService.save/edit/delete`). The domain defines event records; the adapter serializes them.
- **Relay:** `@Scheduled(fixedDelay = 200ms)` in profile `worker`:
  ```sql
  SELECT * FROM outbox WHERE published_at IS NULL ORDER BY id LIMIT 100 FOR UPDATE SKIP LOCKED
  ```
  Then `PublishBatch` (10 per call), mark published, commit. Several worker tasks are safe.
- **Consumer base:** an `adapters/in/messaging/SqsEventListener` (Spring Cloud AWS `@SqsListener`, or a small poller over the SDK) → `IdempotentEventHandler`:
  1. Begin the transaction.
  2. `INSERT processed_event … ON CONFLICT DO NOTHING`; zero rows → skip.
  3. Handle.
  4. Commit.
  5. Delete the SQS message.

  An exception leaves the message invisible until the timeout, then it's retried. After 5 receives it goes to the DLQ.
- **Filter policies:** `indexer` ← `MessageCreated|MessageEdited|MessageDeleted|ChannelReindexRequested`; `notifier` ← `MessageCreated|MemberAdded|MemberRemoved|MessageDeleted`.

## Tasks
- [ ] **P6-T01** Migration: `outbox(id BIGSERIAL, event_id UUID UNIQUE, aggregate_type, aggregate_id, aggregate_version, type, payload JSONB, trace_parent, created_at, published_at)` + a partial index on `published_at IS NULL`; `processed_event(event_id, consumer)` PK
- [ ] **P6-T02** Domain events (`MessageCreated/Edited/Deleted`, `MemberAdded/Removed`) + `OutboxPort` + `JpaOutboxAdapter`. Write them from the P3 services in the same transaction
- [ ] **P6-T03** Terraform (`data` stack, module `sqs-with-dlq`): SNS topic, queues, DLQs, filter policies, DLQ alarms. IAM: `worker` publish/receive scoped
- [ ] **P6-T04** `worker` ECS service in the `app` stack (same image, profile `ecs,worker`), scaling on queue depth. Guard all `@Scheduled` jobs and listeners with the profile
- [ ] **P6-T05** Outbox relay + metrics `outbox.pending`, `outbox.lag.seconds`, `outbox.published`
- [ ] **P6-T06** LocalStack in compose (`--profile events`) + Testcontainers LocalStack module; bootstrap the topic and queues at startup in `local`
- [ ] **P6-T07** Consumer base (idempotency + retry semantics) + tests: duplicate delivery → one effect; out-of-order (v3 then v2) → final state v3; poison message → DLQ
- [ ] **P6-T08** `notifier` consumer:
  - For `MessageCreated`, find members with no live connection (presence) and record a notification (table `notification`, API `GET /notifications`). Email/web push are behind a `NotificationSenderPort` stub.
  - For `MessageDeleted`, delete the media object from S3.
- [ ] **P6-T09** Trace propagation: inject `traceparent` when writing the outbox row, publish it as an SNS attribute, extract it in the listener (span link to the originating request)
- [ ] **P6-T10** Housekeeping: daily job deletes published outbox rows older than 7 days and `processed_event` older than 14 days (> SQS retention)
- [ ] **P6-T11** Runbook in `docs/plans/runbooks/dlq.md`: inspect, fix and redrive (`aws sqs start-message-move-task`)

## Files touched
- `domain/*/event/**`, `domain/common/port/out/OutboxPort.java`
- `adapters/out/persistence/outbox/**`, `adapters/out/messaging/SnsEventPublisherAdapter.java`
- `adapters/in/messaging/**`
- `application/service/notification/**`
- `infra/stacks/{data,app}`, `infra/modules/sqs-with-dlq`
- `docker/local/compose.yaml`

## Test plan
- **IT with LocalStack:** send a message via REST → the outbox row → the relay publishes → the SQS consumer handles it once (Awaitility).
- **Failure injection:** kill the relay after publish and before the mark → the event is published twice → the consumer effect happens once.
- **Unit:** relay batching, serializer, filter mapping.

## Definition of Done
Every message create/edit/delete produces exactly one outbox row in the same transaction. Consumers are provably idempotent and order-tolerant. The DLQ alarm exists, and the redrive runbook has been tried once.

## Interview talking points
- The dual write, and why "publish after commit" loses events.
- `FOR UPDATE SKIP LOCKED` for competing relays.
- At-least-once + idempotency = effectively once. Where the dedupe key lives and why it's in the same transaction as the effect.
- Ordering: FIFO vs version guards vs re-reading current state.
- Why realtime fan-out does **not** go through this pipeline.

## Risks
- Outbox table bloat: the housekeeping job, and optionally a partition by week.
- LocalStack drift from real AWS behaviour: keep one smoke test in dev AWS.
