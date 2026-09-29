# 06 — Event-driven backbone: transactional outbox → SNS → SQS

> **Used in DevCool:** [ADR-0007](../plans/architecture/adr/0007-event-backbone-outbox-sns-sqs.md) · [01 §4.4](../plans/architecture/01-system-architecture.md#44-edit--re-index-async) (edit → re-index flow) · [03 §4](../plans/architecture/03-chat-system-design.md#4-delivery-semantics) · [Phase 6](../plans/phases/phase-6-async-events.md) (P6-T01–T11) · P8-T05 (indexer consumer)
> **Links checked:** 2026-09-29

## Concepts to own

- **The dual-write problem.** Writing to Postgres and publishing to SNS are two systems with no shared transaction. Publish after commit and a crash loses the event; publish inside the transaction and a rollback creates a phantom event.
- **Transactional outbox.** Write the event as a row in the same transaction as the business change. A relay publishes unpublished rows later. The event exists if and only if the transaction committed.
- **Polling publisher vs log tailing.** DevCool polls with `FOR UPDATE SKIP LOCKED`; the alternative is change data capture (Debezium reading the WAL). Know the trade-offs.
- **At-least-once + idempotent consumer = effectively once.** The relay can publish twice (crash between publish and mark). SQS can deliver twice. Consumers dedupe with a `processed_event` row written in the same transaction as their effect.
- **Order tolerance.** Standard SNS/SQS don't preserve order. Consumers re-read current state from the DB and guard writes with `version`, so v2 arriving after v3 is harmless. FIFO topics/queues with `MessageGroupId` are the alternative, with throughput limits.
- **SNS fan-out with filter policies.** One topic, one SQS queue per consumer, each subscription filtering on a `type` message attribute.
- **SQS mechanics.** Visibility timeout (a received message is hidden, not deleted, until you delete it), long polling, `maxReceiveCount` → DLQ, redrive back to the source queue.
- **Events are notifications, the DB is the truth.** Consumers rebuild state from the DB rather than trusting the payload. This is what makes redelivery and reordering safe.
- **Trace context across the async hop.** Put W3C `traceparent` in the outbox row and in SNS message attributes; consumers start a span linked to the producer ([12](12-observability.md)).
- **Kafka vs SNS/SQS vs EventBridge vs Redis Streams.** Replay, ordering, scale to zero and ops burden are the axes in ADR-0007's comparison table.

## Read first

1. [Pattern: Transactional outbox](https://microservices.io/patterns/data/transactional-outbox.html) — *Chris Richardson, microservices.io* · The pattern in one page, with its related patterns (polling publisher, transaction log tailing).
2. [Transactional outbox pattern](https://docs.aws.amazon.com/prescriptive-guidance/latest/cloud-design-patterns/transactional-outbox.html) — *AWS Prescriptive Guidance* · The same pattern on AWS services, with the duplicate-message caveat.
3. [Pattern: Idempotent Consumer](https://microservices.io/patterns/communication-style/idempotent-consumer.html) — *microservices.io* · The consumer side: record processed message ids in the same transaction.
4. [Fanout to Amazon SQS queues](https://docs.aws.amazon.com/sns/latest/dg/sns-sqs-as-subscriber.html) — *AWS docs* · Subscribing queues to a topic, the queue access policy, and raw message delivery.
5. [Amazon SQS visibility timeout](https://docs.aws.amazon.com/AWSSimpleQueueService/latest/SQSDeveloperGuide/sqs-visibility-timeout.html) — *AWS docs* · The single most misunderstood SQS setting; set it longer than your worst processing time.

## Reference

### Outbox and CDC

- [Reliable Microservices Data Exchange With the Outbox Pattern](https://debezium.io/blog/2019/02/19/reliable-microservices-data-exchange-with-the-outbox-pattern/) — *Gunnar Morling, Debezium blog* · The outbox table design explained by a CDC author; good for the "why not Debezium" discussion.
- [Debezium — Outbox Event Router](https://debezium.io/documentation/reference/transformations/outbox-event-router.html) — *official docs* · How CDC turns outbox rows into domain events (Option 6 in ADR-0007).
- [Pattern: Polling publisher](https://microservices.io/patterns/data/polling-publisher.html) and [Pattern: Transaction log tailing](https://microservices.io/patterns/data/transaction-log-tailing.html) — *microservices.io* · The two relay strategies.

### SNS

- [SNS message filtering](https://docs.aws.amazon.com/sns/latest/dg/sns-message-filtering.html) — *AWS docs* · Filter policies on message attributes vs message body; the `type` filter per queue.
- [SNS message attributes](https://docs.aws.amazon.com/sns/latest/dg/sns-message-attributes.html) — *AWS docs* · Where `type` and `traceparent` go.
- [SNS raw message delivery](https://docs.aws.amazon.com/sns/latest/dg/sns-large-payload-raw-message-delivery.html) — *AWS docs* · With raw delivery, SQS gets the body as-is and SNS attributes become SQS attributes; without it, you parse an SNS JSON envelope. Decide before writing the listener.
- [PublishBatch API](https://docs.aws.amazon.com/sns/latest/api/API_PublishBatch.html) — *API reference* · Up to 10 messages per call; partial failures are reported per entry.
- [SNS FIFO topics](https://docs.aws.amazon.com/sns/latest/dg/sns-fifo-topics.html) — *AWS docs* · The ordered alternative (`MessageGroupId = channelId`) and its limits.

### SQS

- [Standard queues — at-least-once delivery](https://docs.aws.amazon.com/AWSSimpleQueueService/latest/SQSDeveloperGuide/standard-queues-at-least-once-delivery.html) — *AWS docs* · Why duplicates happen even without failures on your side.
- [Short and long polling](https://docs.aws.amazon.com/AWSSimpleQueueService/latest/SQSDeveloperGuide/sqs-short-and-long-polling.html) — *AWS docs* · `WaitTimeSeconds` and cost.
- [Dead-letter queues](https://docs.aws.amazon.com/AWSSimpleQueueService/latest/SQSDeveloperGuide/sqs-dead-letter-queues.html) — *AWS docs* · `maxReceiveCount`, retention gotchas.
- [Configure a dead-letter queue redrive](https://docs.aws.amazon.com/AWSSimpleQueueService/latest/SQSDeveloperGuide/sqs-configure-dead-letter-queue-redrive.html) and [StartMessageMoveTask](https://docs.aws.amazon.com/AWSSimpleQueueService/latest/APIReference/API_StartMessageMoveTask.html) — *AWS docs / API* · The redrive runbook in P6-T11.
- [FIFO queues](https://docs.aws.amazon.com/AWSSimpleQueueService/latest/SQSDeveloperGuide/sqs-fifo-queues.html) — *AWS docs* · Message groups, deduplication ids, throughput.
- [Scaling based on Amazon SQS](https://docs.aws.amazon.com/autoscaling/ec2/userguide/as-using-sqs-queue.html) — *AWS docs (EC2 Auto Scaling)* · The "backlog per instance" metric idea; the same reasoning applies to the ECS worker (P6-T04).

### Java and local development

- [Spring Cloud AWS 3.4 reference — SQS integration](https://docs.awspring.io/spring-cloud-aws/docs/3.4.0/reference/html/index.html#sqs-integration) — *official docs* · `@SqsListener`, acknowledgement modes, visibility extension, error handling. 3.4.x is the line for Spring Boot 3.5.
- [LocalStack — SQS](https://docs.localstack.cloud/aws/services/sqs/) — *official docs* · Local SNS/SQS for `--profile events` and Testcontainers (P6-T06).

### Messaging patterns

- [Enterprise Integration Patterns — Idempotent Receiver](https://www.enterpriseintegrationpatterns.com/patterns/messaging/IdempotentReceiver.html) and [Dead Letter Channel](https://www.enterpriseintegrationpatterns.com/patterns/messaging/DeadLetterChannel.html) — *Hohpe & Woolf* · The original pattern language.
- [Amazon EventBridge — what is it](https://docs.aws.amazon.com/eventbridge/latest/userguide/eb-what-is.html) — *AWS docs* · Option 4 in ADR-0007 (archive and replay).
- [Apache Kafka — Design](https://kafka.apache.org/43/design/design/) — *official docs* · Partitions, ordering and retention: Option 3 and the "revisit when".

### Books

- *Microservices Patterns* (Chris Richardson) — ch. 3 (transactional messaging, outbox) and ch. 4 (sagas).
- *Designing Data-Intensive Applications*, 2nd ed. — the stream-processing chapter (logs, CDC, exactly-once).
- *Enterprise Integration Patterns* (Hohpe & Woolf).
- See [17 — Books and courses](17-books-and-courses.md).

## Self-check

- Draw the failure that loses an event with "commit then publish". Draw the one that creates a phantom with "publish then commit".
- Why is the relay at-least-once, not exactly-once? Where exactly is the crash window?
- Why must the `processed_event` insert be in the same transaction as the consumer's effect?
- Consumer receives `MessageEdited v2` after `v3`. What does it do, and which test proves it (P6-T07)?
- What happens to a message whose processing takes longer than the visibility timeout?
- When would you switch to FIFO, and when to Kafka? What stays the same (hint: the outbox)?
- How do you redrive a DLQ safely after fixing the bug?
