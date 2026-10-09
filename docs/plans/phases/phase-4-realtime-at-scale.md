# Phase 4 — Realtime at scale

**Weeks:** 4–6 · **Depends on:** P3 · **ADRs:** 0005, 0006, 0011 · **Design:** [03-chat-system-design.md](../architecture/03-chat-system-design.md) §5–8, §10–11, [`learning/10`](../../learning/10-realtime-architecture-design.md) §7

## Goal
Turn the single-node WebSocket into a multi-node realtime layer:
- Protocol v2.
- Browser-compatible auth.
- Heartbeat and backpressure.
- Redis backplane.
- Presence and typing.
- Resume after reconnect.
- Graceful drain on deploy.
- Autoscaling on connection count.

## Why it matters (interview angle)
This is the heart of any chat system design interview: *"How do two users connected to different servers talk?"*, *"What happens during a deploy?"*, *"How does online status work?"* After this phase, each answer is backed by running code and a test.

## Prerequisites
- P3 (seq, idempotency, message ids in events).
- P2 (two ECS tasks to prove multi-node behaviour).

## Scope
- **In:**
  - Everything in the goal.
  - Valkey in Terraform (`data` stack).
  - A multi-node IT.
- **Out:**
  - Durable offline notifications (P6).
  - AI frames (`ASK`) — protocol reserved, implemented in P8.

## Design notes
- Follow the Stage 1 → Stage 2 order in `learning/10` §7: harden the single node first (decorator, cleanup, per-frame errors), then add the backplane.
- **New ports:**
  - `RealtimeBackplanePort` (publish to channel/user topic, subscribe/unsubscribe per channel).
  - `PresencePort`.
  - `WsTicketPort`.
  - `RateLimitPort`.
- **New inbound use case:** `DeliverToLocalConnectionsUseCase`, called both by the sending node and by the backplane listener.
- **Per-channel subscription reference counting** on each node (atomic `compute` on the map; see the race in `learning/10` §7).
- **Metrics** (used by autoscaling and P7):
  - `ws.connections.active` (gauge)
  - `ws.frames.in{type}`, `ws.frames.out{type}`
  - `ws.delivery.latency` (publish → local send)
  - `ws.backplane.lag`
  - `ws.send.overflow`

## Tasks
### Stage 1: harden a single node
- [ ] **P4-T01** Wrap sessions in `ConcurrentWebSocketSessionDecorator` (sendTimeLimit 5 s, buffer 512 KB, TERMINATE). Clean up on every exit path (`afterConnectionClosed`, `handleTransportError`)
- [ ] **P4-T02** Protocol v2 envelope + frame DTOs + dispatcher (a `Map<type, FrameHandler>` instead of a `switch`). Per-frame try/catch → `NACK`/`ERROR`, never close. Legacy v1 accepted behind a flag
- [ ] **P4-T03** `PING`/`PONG` heartbeat; server-side idle close after 90 s without frames
- [ ] **P4-T04** `EDIT`, `DELETE`, `REACT`, `READ` frames call the P3 use cases, and broadcast `MESSAGE_UPDATED`, `MESSAGE_DELETED`, `REACTION`, `READ_RECEIPT`
### Auth and origin
- [ ] **P4-T05** Valkey in Terraform (`data` stack, ElastiCache Serverless) and in local compose (`valkey/valkey`). Spring Data Redis (Lettuce). **Verify pub/sub support on the serverless offering** and record the result in ADR-0006
- [ ] **P4-T06** `POST /api/v1/ws/ticket` + handshake redeem (`GETDEL`). Reject with 401 on missing or invalid tickets. Origin allowlist from config. Remove the header-based path (or keep it for non-browser clients)
### Stage 2: backplane
- [ ] **P4-T07** Refactor: move fan-out out of `WsSendMessageService` into `DeliverToLocalConnectionsUseCase`. `RealtimeEmitterPort` → `RealtimeBackplanePort`
- [ ] **P4-T08** `RedisBackplaneAdapter`: per-channel topics with ref-counted subscribe/unsubscribe, `originNodeId`/`originConnectionId`, listener hands off to a bounded executor
- [ ] **P4-T09** `RESUME {channelId, lastSeq}` → up to 200 messages in seq order, else `RESYNC_REQUIRED`. Membership checked
### Presence and typing
- [ ] **P4-T10** Presence: per-connection TTL keys + user set; online/offline transitions with a 10 s offline grace; persist `last_seen_at`. Push to channels with ≤ 50 members; `GET /api/v1/presence?userIds=` for pull
- [ ] **P4-T11** Presence sweeper for crashed nodes: the worker/scheduler (profile-guarded) scans expired connections and emits offline events. Use ShedLock or a Redis lock so only one task runs it
- [ ] **P4-T12** Typing: `TYPING` frame → rate limit 1/2 s per connection → publish; never persisted
### Limits, drain, scaling
- [ ] **P4-T13** Rate limits with Bucket4j + Redis: `SEND` 10/s burst 20 per user, `TYPING` per connection; `NACK{code: RATE_LIMITED, retryAfterMs}`
- [ ] **P4-T14** Graceful drain: on `ContextClosedEvent`, send `RECONNECT{afterMs: rand(0..10000)}`, then close 1012. Tune the ALB deregistration delay and ECS stopTimeout (P2 values). Note (P1-T10): ECS deregisters the task from the ALB and waits out the deregistration delay *before* SIGTERM, so `ContextClosedEvent` fires after the delay. Revisit the drain order in [01 §4.5](../architecture/01-system-architecture.md#45-deploy-with-graceful-websocket-drain) and the P2 health-check row
- [ ] **P4-T15** Metrics listed above. Publish `ws.connections.active` to CloudWatch (Micrometer CloudWatch registry, or via the collector in P7). Add a target-tracking scaling policy on it (target 3,000/task)
- [ ] **P4-T16** Multi-node IT: two Spring contexts on random ports sharing a Valkey Testcontainer + Postgres. User A on node 1 and user B on node 2: send, typing, presence, and resume after a forced disconnect
- [ ] **P4-T17** Remove legacy v1 frames once the frontend uses v2 (coordinate with P5)

## Files touched
- `adapters/in/websocket/**`
- `adapters/out/realtime/**` (new `redis/` package)
- `domain/chat/port/**`, a new `domain/presence/**`
- `application/service/chat/**`, `application/service/presence/**`
- `infra/stacks/data` (Valkey), `infra/stacks/app` (scaling policy)
- `docker/local/compose.yaml`

## Test plan
- **Unit:** frame dispatcher, ticket redeem, presence transitions (fake clock), ref-counted subscriptions (concurrency test with many threads).
- **IT:** multi-node chat (T16). Slow-consumer test: a client that doesn't read gets terminated while others still receive.
- **Manual in AWS:** two tasks. Kill one task (`aws ecs stop-task`); clients reconnect and resume with no lost messages.

## Definition of Done
Milestone **M2**: in AWS with 2 tasks, two browsers pinned to different tasks can chat, see presence and typing, and survive a deploy with no lost or duplicated messages.

## Interview talking points
- Why at-most-once pub/sub is enough (DB truth + seq + resume).
- Per-channel topics vs a global topic vs a routing registry.
- Backpressure: one slow client must not delay others.
- Presence with TTLs; crashed nodes; flapping; push vs pull for big groups.
- Graceful drain and reconnect storms (jitter).
- Why tickets instead of tokens in the URL.

## Risks
- Lettuce pub/sub callbacks run on Netty threads. Blocking there stalls every subscription. Always hand off.
- ElastiCache Serverless specifics (cluster mode, sharded pub/sub). Validate early (T05).
- Test flakiness with timing. Use Awaitility, never `Thread.sleep`.
