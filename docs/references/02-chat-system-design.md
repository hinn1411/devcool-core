# 02 — Chat system design

> **Used in DevCool:** [03 — Chat system design](../plans/architecture/03-chat-system-design.md) (all of it) · [ADR-0012](../plans/architecture/adr/0012-message-ordering-per-channel-seq.md) (per-channel seq) · [01 §4 and §7](../plans/architecture/01-system-architecture.md#4-key-flows) · [Phase 3](../plans/phases/phase-3-core-chat.md) · [Phase 4](../plans/phases/phase-4-realtime-at-scale.md) (presence, typing, resume)
> **Already in the repo:** [learning/10 — Realtime architecture design](../learning/10-realtime-architecture-design.md) (options A–K) · [learning/04 — Concurrency and realtime](../learning/04-concurrency-and-realtime.md)
> **Links checked:** 2026-09-29

## Concepts to own

- **Back-of-envelope estimation.** DAU → peak sockets → messages/s → pushes/s → storage/year. The point is to find what breaks first. *In DevCool:* [03 §1](../plans/architecture/03-chat-system-design.md#back-of-envelope-portfolio-target-not-todays-load) shows it's per-process state and slow clients, not Postgres.
- **Ordering scope.** Users need a total order *per channel*, not a global one. Timestamps skew across nodes and tie; global ids (Snowflake, ULID) are roughly time-ordered but can't show a gap. A per-channel counter bumped in the send transaction gives commit order and gap detection. *In DevCool:* `channel.last_seq`, ADR-0012.
- **Gap detection and resume.** A client that holds seq 41 and receives 43 knows 42 is missing and asks `RESUME {lastSeq: 41}`. This is what makes a lossy push acceptable.
- **Idempotency keys.** The client picks `clientMsgId`, the server enforces `UNIQUE (sender_id, client_msg_id)` and returns the original result on retry. At-least-once sending becomes effectively-once.
- **Delivery semantics per leg.** At-most-once (Redis push), at-least-once (client retry, SQS), exactly-once only *inside* one DB transaction. "Exactly-once delivery" across systems is at-least-once plus idempotency.
- **Commit before ACK and before broadcast.** The DB is the source of truth; nothing is announced that could still roll back.
- **Presence.** TTL-refreshed liveness keys per connection, a set per user, a grace period against flapping, and push to small audiences / pull for large ones. Presence fan-out is O(members) per status change.
- **Typing indicators.** Ephemeral, rate-limited, never stored, and expire client-side, so there is no "stopped typing" event to lose.
- **Read state.** `member.last_read_seq` makes unread count a subtraction (`last_seq − last_read_seq`) instead of a `COUNT(*)`.
- **Scaling path.** Partition messages by channel, sharded pub/sub, a dedicated gateway tier, Kafka for replay, multi-region with a home region per channel. Know it, don't build it ([01 §7](../plans/architecture/01-system-architecture.md#7-scaling-path-beyond-this-roadmap)).

## Read first

1. [Design a Chat System](https://bytebytego.com/courses/system-design-interview/design-a-chat-system) — *Alex Xu, System Design Interview vol. 1, ch. 12* · The canonical interview answer. Compare its message-id and sync-queue choices with ADR-0012.
2. [Real-time Messaging](https://slack.engineering/real-time-messaging/) — *Slack Engineering* · How Slack routes messages to gateway servers holding sockets. Maps onto the backplane in ADR-0006.
3. [Designing robust and predictable APIs with idempotency](https://stripe.com/blog/idempotency) — *Stripe* · Idempotency keys and retries with backoff, the pattern behind `clientMsgId`.
4. [You Cannot Have Exactly-Once Delivery](https://bravenewgeek.com/you-cannot-have-exactly-once-delivery/) — *Tyler Treat* · Why "exactly-once" is really at-least-once plus idempotent processing.
5. [How Discord Stores Trillions of Messages](https://discord.com/blog/how-discord-stores-trillions-of-messages) — *Discord* · What the 100× scaling path looks like in production (partition by channel + time bucket).

## Reference

### Case studies

- [How Discord Stores Billions of Messages](https://discord.com/blog/how-discord-stores-billions-of-messages) — *Discord* · The earlier post: why they moved from MongoDB to Cassandra, and the `(channel_id, bucket)` partition key.
- [How Discord Scaled Elixir to 5,000,000 Concurrent Users](https://discord.com/blog/how-discord-scaled-elixir-to-5-000-000-concurrent-users) — *Discord* · Fan-out to large guilds, and why big-channel fan-out needs special handling.
- [Flannel: An Application-Level Edge Cache to Make Slack Scale](https://slack.engineering/flannel-an-application-level-edge-cache-to-make-slack-scale/) — *Slack* · Lazy-loading team state instead of pushing everything on connect; related to "pull presence for big channels".
- [Design a Messaging App Like WhatsApp](https://www.hellointerview.com/learn/system-design/problem-breakdowns/whatsapp) — *Hello Interview* · A second interview-style breakdown with offline delivery and multi-device.

### Ordering, ids and time

- [Time, Clocks, and the Ordering of Events in a Distributed System](https://lamport.azurewebsites.net/pubs/time-clocks.pdf) — *Leslie Lamport, 1978 (paper)* · Why wall-clock time cannot order events across machines.
- [Snowflake (Twitter, 2010 release)](https://github.com/twitter-archive/snowflake/tree/snowflake-2010) — *source + README* · The 64-bit time/worker/sequence id layout; the "global id" option rejected in ADR-0012.
- [ULID spec](https://github.com/ulid/spec) — *spec* · Sortable 128-bit ids; another rejected option, and why sortable ≠ gap-detectable.

### Idempotency and delivery

- [Implementing Stripe-like Idempotency Keys in Postgres](https://brandur.org/idempotency-keys) — *Brandur Leach* · A full Postgres implementation with recovery points; deeper than DevCool needs, but the reasoning carries over.
- [The Idempotency-Key HTTP Header Field](https://datatracker.ietf.org/doc/draft-ietf-httpapi-idempotency-key-header/) — *IETF draft* · The standard shape for REST idempotency, if the REST send path ever needs it.

### Estimation

- [Latency Numbers Every Programmer Should Know](https://gist.github.com/jboner/2841832) — *Jeff Dean / Jonas Bonér* · The numbers to reason with in back-of-envelope sums.

### Books

- *System Design Interview*, vol. 1 (Alex Xu) — ch. 2 (back-of-envelope), ch. 7 (unique id generator), ch. 12 (chat system).
- *Designing Data-Intensive Applications*, 2nd ed. (Kleppmann & Riccomini) — the chapters on replication, transactions and "the trouble with distributed systems" (clocks, ordering).
- See [17 — Books and courses](17-books-and-courses.md).

## Self-check

These are the questions from [03 §13](../plans/architecture/03-chat-system-design.md#13-interview-checklist). Answer each in two sentences:

- Why commit before ACK, and before broadcast?
- How is a duplicate send detected, and what does the second ACK contain?
- Why a per-channel seq and not timestamps or a global id?
- Why can the push be at-most-once while the system still loses nothing?
- How do two users on different ECS tasks talk to each other?
- How does presence survive a crashed task?
- Why is typing never persisted, and why is there no "stopped typing" event?
- How is the unread count O(1)?
- What are the first three things you'd change at 100× load?
