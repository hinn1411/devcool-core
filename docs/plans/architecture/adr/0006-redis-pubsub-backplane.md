# 0006 — Valkey/Redis pub/sub backplane with per-channel topics

- **Status:** Accepted
- **Date:** 2026-09-29
- **Phase:** P4

## Context
`learning/10-realtime-architecture-design.md` analyses options A–K and recommends a backplane with local registries (Stage 2) "when a second task is needed". This roadmap needs ≥ 2 tasks for availability and for the deploy story, so Stage 2 is now in scope. The same store must also hold presence, WS tickets and rate limits, all of which are per-process state today or don't exist yet.

## Options considered
Summary. The full analysis is in `learning/10` §6.

| Option | Delivery | New infra | Also solves presence/tickets/rate limits? | Notes |
|---|---|---|---|---|
| **B. Redis/Valkey pub/sub** | at-most-once | Valkey | **Yes** | Sub-ms. Per-channel topics limit fan-in |
| C. PG `LISTEN/NOTIFY` | at-most-once | none | No | 8 KB payload limit; a listener connection per task; adds load to the primary DB |
| J. SNS → SQS queue per node | at-least-once | SNS + dynamic SQS | No | Queue per task lifecycle is awkward; adds 10–100 ms |
| I. Kafka | at-least-once | cluster | No | Replay you don't need on the hot path |
| Redis Streams | at-least-once | Valkey | Yes | Consumer groups are the wrong model for "every node sees it"; you'd need a stream per node or fan-out reads |

## Decision
**B, with per-channel topics.** A node subscribes to `chat:ch:{channelId}` when its first local connection subscribes to that channel, and unsubscribes when its last leaves. User-scoped events (presence, "you were removed from channel") use `chat:user:{userId}`, subscribed while that user has a local connection.

Hosted on **ElastiCache Serverless for Valkey** in AWS and `valkey/valkey` locally. The client is Lettuce, via Spring Data Redis.

## Consequences
- The fan-out loop moves out of `WsSendMessageService` into a `DeliverToLocalConnections` use case, called both directly (the sender's node) and by the backplane listener (other nodes). This is the refactor described in `learning/10` §7.
- Payload carries `originNodeId` and `originConnectionId`, so a node ignores its own echo and skips the sender.
- Lost pushes are recovered through `RESUME` (ADR-0012). The client must implement it.
- Subscribe/unsubscribe must be atomic per channel on a node (see the race in `learning/10` §7 caveat). Use `compute` on the local map and reference-count subscriptions.
- **Verify in P4** that ElastiCache Serverless supports the pub/sub commands used (`SUBSCRIBE`/`PUBLISH`, or sharded `SSUBSCRIBE`/`SPUBLISH`) and note the result here.

## Revisit when
- Redis pub/sub network egress or CPU is measured as the bottleneck: move to sharded pub/sub or channel-hash routing (options F/G).
- Pushes must be durable (e.g. offline mobile push): add a durable path, don't make pub/sub durable.
