# 04 — Redis / Valkey: backplane, presence, tickets, rate limits

> **Used in DevCool:** [ADR-0006](../plans/architecture/adr/0006-redis-pubsub-backplane.md) (pub/sub backplane, per-channel topics) · [ADR-0011](../plans/architecture/adr/0011-ws-ticket-auth-single-origin.md) (tickets with `GETDEL`) · [03 §6–§8](../plans/architecture/03-chat-system-design.md#6-fan-out-across-nodes) · P4-T05–T13 · P8-T17 (AI budget), P8-T21 (summary cache)
> **Already in the repo:** [learning/10 §6–§9](../learning/10-realtime-architecture-design.md) (backplane options, the subscribe/unsubscribe race)
> **Links checked:** 2026-09-29

## Concepts to own

- **Pub/sub is fire-and-forget.** A message goes to whoever is subscribed *right now*. No persistence, no replay, no ACK: at-most-once. *In DevCool:* acceptable because `RESUME` recovers from the DB.
- **Sharded pub/sub (`SPUBLISH`/`SSUBSCRIBE`).** In cluster mode, classic `PUBLISH` is broadcast to every node; sharded pub/sub routes a channel to the shard that owns its hash slot. **ElastiCache Serverless implements `PUBLISH`/`SUBSCRIBE` with sharded pub/sub internally, and pattern subscriptions (`PSUBSCRIBE`) are not available there.** Check this against the design in P4-T05.
- **Per-channel topics and reference counting.** A node subscribes to `chat:ch:{id}` on its first local subscriber and unsubscribes on its last. Subscribe/unsubscribe must be atomic per channel on a node.
- **TTL keys as liveness.** `SET key value EX 60` refreshed by heartbeats; expiry means "dead" without anyone running cleanup. *In DevCool:* `presence:conn:{connId}`.
- **Keyspace notifications.** Redis can publish an event when a key expires, but expiry is lazy/sampled (not exact), delivery is fire-and-forget, and it needs `notify-keyspace-events` config, which a managed service may not let you set. A periodic sweeper is the safer fallback (P4-T11).
- **Atomic single-use tokens.** `GETDEL` reads and deletes in one step, so a ticket can be redeemed exactly once even with concurrent attempts.
- **Lua scripts / atomic multi-step logic.** Run server-side in one step; in cluster mode all keys must hash to one slot (use hash tags `{…}`).
- **Token bucket rate limiting.** Capacity + refill rate; allows bursts, bounds the average. Shared state in Redis makes the limit hold across N tasks. *In DevCool:* Bucket4j on Lettuce.
- **Distributed locks and leader election.** "Only one task runs the sweeper" needs a lock with a TTL; know why Redis locks are fine for efficiency but not for correctness (fencing tokens).
- **Valkey vs Redis.** Valkey is the Linux Foundation fork of Redis 7.2 (after the 2024 licence change), wire-compatible; AWS prices ElastiCache for Valkey lower.

## Read first

1. [Valkey — Pub/Sub](https://valkey.io/topics/pubsub/) — *official docs* · Semantics, pattern subscriptions, sharded pub/sub, and the "at-most-once" delivery guarantee in the project's own words.
2. [ElastiCache — Supported and restricted commands](https://docs.aws.amazon.com/AmazonElastiCache/latest/dg/SupportedCommands.html) — *AWS docs* · The ground truth for what works on serverless. Read the pub/sub section and the restricted-commands list (`CONFIG` is restricted) before building presence or the sweeper.
3. [Spring Data Redis — Pub/Sub messaging](https://docs.spring.io/spring-data/redis/reference/3.5/redis/pubsub.html) — *official docs* · `RedisMessageListenerContainer`, dynamic subscribe/unsubscribe, and listener threading; the base for `RedisBackplaneAdapter`.
4. [Bucket4j documentation](https://bucket4j.com/) — *official docs* · Token bucket model, then the "distributed" chapter for the Lettuce-based proxy manager (P4-T13).
5. [How to do distributed locking](https://martin.kleppmann.com/2016/02/08/how-to-do-distributed-locking.html) — *Martin Kleppmann* · Why a Redis lock needs fencing tokens for correctness; relevant to the presence sweeper and ShedLock.

## Reference

### Commands and data structures

- [SSUBSCRIBE](https://valkey.io/commands/ssubscribe/) — *command reference* · Sharded subscribe, slot ownership, and what happens on resharding.
- [Keyspace notifications](https://valkey.io/topics/notifications/) — *official docs* · Event types, the config flag, and when `expired` events actually fire (on access or background sweep, not at the exact TTL instant).
- [EXPIRE](https://valkey.io/commands/expire/) — *command reference* · How expiry works (passive + active), relevant to presence accuracy.
- [GETDEL](https://valkey.io/commands/getdel/) — *command reference* · The atomic redeem used for WS tickets.
- [SET](https://valkey.io/commands/set/) — *command reference* · `NX`, `EX`/`PX`, `GET` options; the building blocks for locks and TTL keys.
- [Introduction to Eval / Lua scripting](https://valkey.io/topics/eval-intro/) — *official docs* · Atomic scripts; the cluster key rules.
- [Streams introduction](https://valkey.io/topics/streams-intro/) — *official docs* · Consumer groups, `XACK`, `XCLAIM`; the option ADR-0006 and ADR-0007 compare against.

### Managed service (ElastiCache Serverless)

- [ElastiCache — Choosing between deployment options](https://docs.aws.amazon.com/AmazonElastiCache/latest/dg/WhatIs.deployment.html) — *AWS docs* · Serverless vs node-based clusters.
- [ElastiCache pricing](https://aws.amazon.com/elasticache/pricing/) — *AWS* · Serverless minimum charges per GB-hour and ECPU; the "minimum monthly charge even when idle" in [02-tech-stack](../plans/architecture/02-tech-stack.md).
- [Spring Integration issue #10471 — PUB_SUB lock on ElastiCache Valkey Serverless](https://github.com/spring-projects/spring-integration/issues/10471) — *GitHub issue* · A real report of pub/sub-based behaviour differing on serverless; read before relying on pub/sub-driven locks.

### Java clients and Spring

- [Lettuce reference guide](https://redis.github.io/lettuce/) — *official docs* · Connection model (one shared connection, event loop), pub/sub connections, cluster support. Never block inside a Lettuce callback.
- [Spring Boot — Redis](https://docs.spring.io/spring-boot/3.5/reference/data/nosql.html#data.nosql.redis) — *official docs* · Auto-configuration and `spring.data.redis.*` properties.
- [ShedLock](https://github.com/lukas-krecan/ShedLock) — *README* · "At most one node runs this `@Scheduled` job", with a Redis provider (P4-T11).

### Rate limiting

- [Scaling your API with rate limiters](https://stripe.com/blog/rate-limiters) — *Stripe* · Four kinds of limiters (request rate, concurrency, fleet usage, worker utilization) and how Stripe uses token buckets in Redis.
- [How we built rate limiting capable of scaling to millions of domains](https://blog.cloudflare.com/counting-things-a-lot-of-different-things/) — *Cloudflare* · Sliding-window approximation and the memory trade-offs.

### Books

- *Designing Data-Intensive Applications*, 2nd ed. — the distributed-systems chapter on leases, fencing tokens and process pauses.
- See [17 — Books and courses](17-books-and-courses.md).

## Self-check

- What exactly is lost when a node is briefly disconnected from Valkey, and how does DevCool get it back?
- Why per-channel topics instead of one global topic? What does it cost?
- What does ElastiCache Serverless do differently for `PUBLISH`/`SUBSCRIBE`, and which command is unavailable?
- Why is `GETDEL` safe for single-use tickets when `GET` followed by `DEL` is not?
- Why can't presence rely only on keyspace expiry events to mark users offline?
- Explain a token bucket with capacity 20 and refill 10/s. What does a burst of 25 sends in one second get?
- When is a Redis lock "good enough", and when do you need fencing tokens?
