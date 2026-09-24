# 10 — Realtime Architecture Design

Chapter 04 lists the realtime *bugs*. This chapter is the map that was missing: how the `/ws` layer is put together, where it stops scaling, and what to move to when it does.

It assumes **one ECS task today** and asks what the path to several looks like. Options B-K in §6 are **designs, not code**. None of them exists in the repo.

**How to read the claims**

| Marker | Meaning |
|---|---|
| `file:line` | Read in this repo on 2026-09-24 |
| *(docs)* | Checked against the Spring 6.2, PostgreSQL or Redis documentation on 2026-09-24 |
| *(general)* | Not checked here. Verify before you decide |

---

## 1. The design on one page

```
 client ⇄ /ws   Authorization: Bearer <jwt>
    │
 ┌──▼─ adapters/in ────────────────────────────────────────┐
 │ JwtAuthFilter ▶ WsAuthHandShakeInterceptor              │
 │ ▶ RawWebSocketHandler   (one switch on frame.action)    │
 └────────┬─────────────────────────┬──────────────────────┘
          │ SUBSCRIBE               │ SEND_MESSAGE
 ┌────────▼─ application ───────────▼──────────────────────┐
 │ WsSubscribeService        WsSendMessageService          │
 │ WsUnsubscribeService = empty stub, not wired            │
 └────────┬─────────────────────────┬──────────────────────┘
          │ outbound ports          │
 ┌────────▼─────────────────────────▼──────────────────────┐
 │ ConnectionRegistryPort      RealtimeEmitterPort         │
 └────────┬─────────────────────────┬──────────────────────┘
 ┌────────▼─ adapters/out ──────────▼──────────────────────┐
 │ InMemoryConnectionRegistry-   WsRealtimeEmitterAdapter  │
 │ Adapter (2 ConcurrentHashMaps)  └▶ WsSessionStore       │
 └─────────────────────────────────────────────────────────┘
```

`WsSendMessageService` also calls `SaveMessageUseCase` (→ `MessageService` → PostgreSQL) before it touches the registry. Everything realtime-specific sits behind the two ports. That is what makes the options in §6 possible.

**The protocol.** One endpoint, `/ws` (`WebSocketConfig.java:21`), one JSON frame per message.

| Direction | Frame | Handled? | Where |
|---|---|---|---|
| client → server | `PING` | yes, replies `ACK "PONG"` | `RawWebSocketHandler:67-72` |
| client → server | `SUBSCRIBE` (`channelId`) | yes | `:73-83` |
| client → server | `SEND_MESSAGE` (`channelId`, `contentType`, `content`) | yes | `:84-96` |
| client → server | `UNSUBSCRIBE` | declared in `WsMessageType`, **no case**, so `ERROR "Unsupported action"` | `:97-103` |
| server → client | `ACK` / `ERROR` as `WsServerFrame(type, clientMsgId, data)` | after each frame | `WsServerFrame.java` |
| server → client | push as `OutboundWsEvent(type, channelId, senderId, contentType, content)` | to other connections | `WsSendMessageService:58` |

Two envelope shapes share one socket. `"MESSAGE"` is a plain string, since `WsMessageType` has no such constant, so a client has to tell the shapes apart by their fields.

---

## 2. The identity model

```
 user ──1:N── connection ──N:M── channel
```

| Value | Who supplies it | Source |
|---|---|---|
| `connectionId` | **server** | `session.getId()` (`RawWebSocketHandler:37,43,109`), a UUID string from Spring's `StandardWebSocketSession` (6.2.11, read from the jar) |
| `userId` | **server** | the JWT principal, stored as a handshake attribute (`WsAuthHandShakeInterceptor:46`) |
| `channelId` | client | the frame field |

The server builds `SubscribeCommand(connectionId, userId, channelId)` itself (`RawWebSocketHandler:74-75`). The client never sends, and never learns, its `connectionId`. This is a security property: a client cannot subscribe someone else's connection or claim another user id, because it cannot name either.

Two consequences that matter later:

- **Subscription is not membership.** Membership lives in the DB and is checked once, at subscribe time (`WsSubscribeService:40-50`). The registry only records *who is listening right now*.
- **The sender is skipped by connection, not by user** (`WsSendMessageService:42-44`). The same user's second tab or phone is a different connection and still receives the message. Clients must render an event whose `senderId` is their own user id as "mine".

---

## 3. Lifecycle

**Connect** (`RawWebSocketHandler:34-39`)
1. The handshake interceptor refuses the upgrade unless the principal is a user id.
2. `sessionStore.put(session)` and `registerConnection(connectionId, userId)`.
3. Nothing is sent to the client.

**Subscribe** (`RawWebSocketHandler:73-83`, `WsSubscribeService:28-53`, `@Transactional`)
1. Channel exists, else `ChannelNotFoundException`.
2. User is a member, else `MemberNotFoundException`.
3. `registry.subscribe(connectionId, channelId)`, then `ACK "Subscribe successfully"`.
4. One frame subscribes one channel. Send several frames to subscribe to several channels.

**Send a message**

```
 sender      handler       WsSendMessageService        PG        other sockets
   │─ SEND ──▶│                    │                     │              │
   │          │─ sendMessage ─────▶│                     │              │
   │          │                    │─ save (tx) ────────▶│              │
   │          │                    │◀──── id, COMMIT ────│              │
   │          │                    │ registry: who is on channel?       │
   │          │                    │ for each conn ≠ sender's:          │
   │          │                    │────── OutboundWsEvent ────────────▶│
   │          │◀───── returns ─────│                                    │
   │◀─ ACK "SENT" (:92-95)         │                                    │
```

The ordering is the key design fact. `MessageService.save` is `@Transactional` (`MessageService:48-49`) and returns the new `Integer` id. It commits when it returns. `WsSendMessageService` has no transaction of its own, so the broadcast happens **after** the commit. The database is the source of truth, and a failed broadcast can never lose a message. (Line 30 discards the returned id, which §5.4 comes back to.)

**Disconnect** (`RawWebSocketHandler:107-112`)
1. `sessionStore.remove` and `registry.removeConnection`.
2. `handleTransportError` is not overridden.
3. `removeConnection` clears `connectionToUser` and removes the id from **every** channel's set, so only that connection leaves its channels. The empty sets are kept. This was fixed on 2026-09-24. 04 §3 describes the earlier behaviour, where the id stayed in every channel set. One edge case remains: a `subscribe` that races with the disconnect can re-add the id after the cleanup has passed that set (04 §1).

---

## 4. What the design gets right

Keep these whatever you choose in §6.

- **Persist, then broadcast.** Only committed messages are ever pushed (`WsSendMessageService:30` before `:43`).
- **Server-derived identity.** §2. No client-supplied user or connection id.
- **Authorization at subscribe.** Channel and membership are checked before the registry is touched.
- **Framework-free ports.** `ConnectionRegistryPort` and `RealtimeEmitterPort` take a `String` connection id, not a `WebSocketSession`. Swapping the adapter does not touch `domain/`.
- **A catch-up path already exists.** `GET /api/v1/channels/{id}/messages?cursorId=` is built and working. It is what makes a lossy push acceptable (§7).
- **The REST side is stateless.** `SessionCreationPolicy.STATELESS` (`SecurityConfig:49`), and a grep of `src/main` for `ConcurrentHashMap`, `@Scheduled`, `@Cacheable` and static maps finds nothing outside the WebSocket and realtime adapters. The WebSocket layer is the only thing that pins you to one task.

---

## 5. Where it stops scaling

### 5.1 State lives inside one JVM

**The concept.** A `ConcurrentHashMap` is shared by threads, not by processes. With two tasks you have two registries, and each knows only its own sockets.

**In your code.** `InMemoryConnectionRegistryAdapter` (the `connectionToUser` and `channelToConnections` fields) and `WsSessionStore:11`.

```
   A ⇄ ┌────────────────────┐          ┌────────────────────┐ ⇄ B
       │ Task 1             │          │ Task 2             │
       │ ch100 → {A}        │    ✗     │ ch100 → {B}        │
       │ A sends to ch100 ──┼──────────┼─▶ never arrives    │
       └────────────────────┘  no path └────────────────────┘
```

**Why it matters.** The message is saved, so B sees it on the next REST fetch. B never gets it live. Nothing fails, so nothing alerts.

**Sticky sessions do not fix this.** They pin a *client* to a node. A and B are both correctly pinned, and they are still on different nodes.

### 5.2 Sending is blocking and unsynchronized

The fan-out runs on the *sender's* thread and calls `session.sendMessage` directly (`WsRealtimeEmitterAdapter:23-34`). Spring's reference documentation says *(docs)* that with the raw `WebSocketHandler` API the application must synchronize sending, because the underlying standard (JSR-356) session does not support concurrent sends, and that `ConcurrentWebSocketSessionDecorator` is the tool for it. Two consequences:

- **Concurrent writes** to one recipient from two senders' threads. Details and fix: 04 §2. The failing behaviour is documented but was not reproduced in this repo.
- **One slow client stalls the sender.** The loop is sequential and each send blocks (04 §2), so the sender's `SENT` ACK waits for the slowest recipient. The decorator's send-time and buffer limits *(docs)* are the missing bound.

### 5.3 Restart and deploy drop everything

Registry and session store are in memory. Every deploy closes every socket and forgets every subscription. Clients must reconnect, subscribe again, and re-fetch what they missed over REST. This is tolerable when it is rare, and painful when the API deploys often.

### 5.4 Events cannot be ordered, deduplicated or resumed

`OutboundWsEvent` has no message id and no timestamp (`WsSendMessageService:58`). `SaveMessageUseCase.save` returns the id and `:30` throws it away. A client cannot tell whether it already has a message, and cannot ask for "everything after id 42". Chapter 04 §6 covers the fix. Every backplane option in §6 makes this more important, because they all deliver at-most-once or at-least-once, never exactly-once.

### 5.5 Handshake authentication depends on the client

`JwtAuthFilter:32` reads only the `Authorization: Bearer` header. `/ws` is `permitAll` (`SecurityConfig:41`) and `WsAuthHandShakeInterceptor` is the real gate. A browser's `WebSocket` constructor cannot set custom headers *(general)*, so a browser client needs another mechanism: a token in the query string, a subprotocol, or a short-lived one-time ticket. A native mobile client can send the header as is. **This depends on a client type this repo does not reveal.** It is not a scaling limit, but it changes what the design must look like, so it is listed here.

### 5.6 No capacity numbers exist

Nothing in the repo measures connections per task, send latency or the largest channel. Before choosing anything past Stage 1 in §7, measure three things: sockets held per task, p95 duration of `sendMessage` (save plus fan-out), and fan-out time for your biggest channel. Any threshold in this chapter would be invented.

---

## 6. Options

Every option keeps `save` before the broadcast. The letters match the earlier discussion, so A-E are the original list and F-K were added.

Diagram legend: `Task N` = an ECS task, `PG` = PostgreSQL.

### Family 1: stay on one node

#### A. Harden the single node

*In the hexagon:* no port change. Adapters get safer: `ConcurrentWebSocketSessionDecorator` in `WsSessionStore`, a reverse index in the registry.

```
 A ─┐
 B ─┼─ WS ─▶ ALB ─▶ ┌───────────────── Task 1 ─────────────────┐
 C ─┘               │ registry + sessions = local maps         │
                    │ sends go through ConcurrentWebSocket-    │
                    │ SessionDecorator (queue + time/size cap) │
                    └────────────────────┬─────────────────────┘
                                         └── save ──▶ PG
```

- **Pros:** no new infrastructure; small change; every other option reuses this "deliver to my local connections" code; fixes the bugs that hurt today.
- **Cons:** still one node (a single point of failure, and only vertical scaling); every deploy drops all sockets.
- **Fits when:** one task and modest traffic. This is where you are.

### Family 2: a backplane, each node keeps a local registry

Every node still tracks only its own sockets. A message is published once and every node delivers it to its own subscribers.

#### B. Redis Pub/Sub

*In the hexagon:* a new `RealtimeEmitterPort.broadcastToChannel`. A `RedisBackplaneAdapter` publishes, and a listener on each node calls a local-delivery use case.

```
  A          B          C       1. Task 1: save ──▶ PG
  │          │          │       2. Task 1: PUBLISH chat:100
  ▼          ▼          ▼       3. Redis ──▶ every task (all SUBSCRIBE)
┌──────┐  ┌──────┐  ┌──────┐    4. each task delivers to ITS OWN
│Task 1│  │Task 2│  │Task 3│       connections of channel 100
└──┬───┘  └──┬───┘  └──┬───┘       (Task 1 skips the sender's connection)
   └─────────┼─────────┘
             ▼
       ┌───────────┐
       │   Redis   │  fire-and-forget, nothing stored
       └───────────┘
```

- **Pros:** the smallest step to multi-node; the registry stays local, so there is no shared mutable state; very low latency; ElastiCache is managed.
- **Cons:** new infrastructure and cost. Delivery is at-most-once *(docs)*: a subscriber that is offline "misses it permanently". Redis's own guidance is the pattern this app already has: write the durable record first, then `PUBLISH`, and let reconnecting consumers reconcile from the store. Every node receives every event, even with no local subscriber.
- **Variant:** Redis Streams *(docs)* persist messages and support at-least-once, for more machinery.
- **Fits when:** two or more tasks, chat-style traffic, and you accept catch-up on reconnect.

#### C. PostgreSQL `LISTEN/NOTIFY`

*In the hexagon:* the same port as B. The adapter runs `NOTIFY` inside the save transaction, and each node holds a `LISTEN` loop.

```
 Task 1:  BEGIN
            INSERT message
            NOTIFY chat, '{"ch":100,"id":42}'   (held until COMMIT)
          COMMIT ────────────────────────────┐
                                             ▼
                                       ┌────────────┐
                                       │     PG     │
                                       └─────┬──────┘
                    LISTEN chat              │            LISTEN chat
                 ┌───────────────────────────┴────────────┐
                 ▼                                        ▼
            ┌────────┐                               ┌────────┐
            │ Task 1 │ → local connections           │ Task 2 │ → its own
            └────────┘                               └────────┘
```

- **Pros:** no new infrastructure. *(docs)* Notifications sent inside a transaction are queued and delivered only after it commits, and arrive in commit order. That is exactly the "persist, then broadcast" ordering you want.
- **Cons:** *(docs)* the payload must be under 8000 bytes by default, and identical payloads in one transaction collapse into one event, so send ids, not content. *(general)* Each node needs its own long-lived session, which is a problem behind a transaction-pooling proxy. It adds load to the database you already depend on, and it is at-most-once.
- **Fits when:** two or three tasks and you want no new service.

#### J. AWS-native fan-out: SNS → one SQS queue per node

*In the hexagon:* the same port as B. An `SnsBackplaneAdapter` publishes, and a listener long-polls the node's own queue.

```
 Task 1 ── save ──▶ PG
    └─ publish ──▶ ┌───────────────┐
                   │  SNS topic    │
                   │ "chat-events" │
                   └───┬───┬───┬───┘
              ┌────────┘   │   └────────┐
              ▼            ▼            ▼
         SQS q-task1  SQS q-task2  SQS q-task3     each queue created at
              │            │            │           boot, deleted at shutdown
              ▼ long poll  ▼            ▼
           Task 1       Task 2       Task 3  → deliver to local connections
```

- **Pros:** *(general)* fully managed and durable, so a node that is briefly down still gets its backlog; at-least-once; no Redis to run; IAM auth fits the AWS setup you already have.
- **Cons:** *(general)* a per-node queue lifecycle (create at boot, delete at shutdown, and orphans after a crash need a reaper or short retention); higher latency than Redis; per-request cost; every node still gets every event; at-least-once means duplicates, so dedupe by message id.
- **Fits when:** you are AWS-only and prefer managed services to running Redis.

#### I. Log-based backplane (Kafka, Kinesis, NATS JetStream)

*In the hexagon:* the same port, with a producer adapter and a consumer adapter per node.

```
 Task 1 ── save ──▶ PG
    └─ produce (key = channelId) ──▶ ┌──────────────────────────┐
                                     │ topic "messages"         │
                                     │ P0 ▮▮▮▮▮  P1 ▮▮▮▮  P2 ▮▮▮ │  ordered per
                                     └────┬───────┬───────┬─────┘  partition
                       each node = its    │       │       │
                       OWN consumer group ▼       ▼       ▼
                                       Task 1  Task 2  Task 3 → local delivery
```

- **Pros:** *(general)* durable and replayable (a node resumes from its last offset); ordering per channel via the partition key; the same log can feed push notifications, search and analytics.
- **Cons:** the heaviest to run and pay for; every node still reads every partition; ephemeral consumer groups per node leave junk on scale-in; more latency than Redis; overkill at this app's scale.
- **Fits when:** you also need an event log for other consumers, at real scale.

### Family 3: smarter routing, so not every event reaches every node

#### F. Shared registry in Redis with direct node routing

*In the hexagon:* `ConnectionRegistryPort` gets a Redis-backed adapter that tracks `channel → nodes`, and the emitter port targets nodes instead of connections.

```
 Task 1 ── 1 save ──▶ PG
    │  2 SMEMBERS ch:100:nodes ──▶ Redis ──▶ { task-1, task-2 }
    │  3 deliver to local connections of ch 100
    └─ 4 PUBLISH inbox:task-2 (event) ──▶ Redis ──▶ Task 2 ──▶ local delivery
 Redis keys:  ch:{id}:nodes   nodes with ≥1 local subscriber (refcounted)
              node:{id}:hb    heartbeat with TTL, to expire dead nodes
```

- **Pros:** a node only receives events for channels it has subscribers in; scales further than B when channels are many and small.
- **Cons:** the registry is now shared distributed state. A crashed node leaves stale entries (the problem `removeConnection` used to have on one node, now across nodes), which needs TTL heartbeats and reconciliation. Each message costs an extra Redis round trip, each node must refcount its local subscribers per channel, and there is noticeably more code than B.
- **Fits when:** many nodes and many channels, and B's "everyone gets everything" has been *measured* as the bottleneck.

#### G. Channel-sharded routing (consistent hash on `channelId`)

*In the hexagon:* the registry stays local and correct. A routing layer sends every member of a channel to the same node.

```
 client ── WS (shard = hash(channelId) mod N) ──▶ Task k
            ┌────────────────────────────────────────────┐
 ch 100 ───▶│ Task 1  owns ALL sockets of ch 100, 103 ...│
 ch 205 ───▶│ Task 2  owns ALL sockets of ch 205, ...    │  no backplane
            └────────────────────────────────────────────┘
 a user in ch 100 and 205 opens 2 sockets (or a gateway multiplexes: see H)
```

- **Pros:** no backplane; the local registry is enough; per-channel ordering is trivial.
- **Cons:** the client opens up to one socket per shard, or you need a routing gateway. *(general)* An ALB cannot hash on an application-level channel id, so the shard has to be computed on the client (for example `/ws?shard=k`, one target group per shard) or by a proxy that can hash. Changing N moves live connections, and one hot channel is one hot node that cannot be spread.
- **Fits when:** channels are small and bounded, and clients can handle several sockets.

### Family 4: change the topology

#### D. STOMP with an external broker relay (RabbitMQ, ActiveMQ)

*In the hexagon:* the raw handler and custom frames are replaced by `@MessageMapping` controllers. The broker replaces most of the registry and emitter ports, and auth and membership move to a `ChannelInterceptor`.

```
 clients ⇄ ┌────────┐
 clients ⇄ │ Task 1 │──┐        STOMP relay
           └────────┘  │      ┌───────────────────────────────┐
 clients ⇄ ┌────────┐  ├────▶ │ RabbitMQ / ActiveMQ            │
 clients ⇄ │ Task 2 │──┘◀──── │ /topic/channel.100             │
           └────────┘  push   │ broker owns subscriptions and  │
                              │ fan-out to the right task      │
                              └───────────────────────────────┘
 client: SUBSCRIBE /topic/channel.100      SEND /app/chat.100
```

- **Pros:** *(docs)* with a full-featured broker each app instance connects to the broker, and a message broadcast from one instance is routed to clients on any other. Spring's simple in-memory broker does not support multiple instances. Subscribe, unsubscribe, heartbeats and fan-out come built in and battle-tested.
- **Cons:** it rewrites the protocol, so every client changes; you run and operate a broker; per-destination authorization has to be written as interceptors; `CLAUDE.md` records the raw protocol as a design choice, so this reverses it.
- **Fits when:** you would rather adopt a standard protocol than maintain a custom one.

#### H. A dedicated WebSocket gateway plus an internal bus

*In the hexagon:* two deployables. The gateway holds `/ws`, auth, connections and delivery. The chat API keeps REST and persistence and publishes `message.created`.

```
 clients ⇄ ┌────────────────────┐  1 SEND_MESSAGE  ┌───────────────────┐
 clients ⇄ │ WS Gateway  × N    │─────────────────▶│ Chat API  × M     │
           │ auth, sockets,     │  (HTTP / gRPC)   │ REST + persistence│──▶ PG
           │ subscribe, deliver │                  └─────────┬─────────┘
           └─────────▲──────────┘                            │ 2 publish
                     │                                       ▼
                     │ 3 message.created            ┌─────────────────┐
                     └──────────────────────────────┤ Redis / NATS    │
                                                    └─────────────────┘
```

- **Pros:** API deploys no longer drop sockets, which is the biggest operational win; connections and REST scale independently; the gateway is thin and rarely changes; failure domains are clear.
- **Cons:** two deployables plus a bus; a service-to-service call and auth on the send path; harder to debug across services; you still need a backplane between gateways (the bus provides it).
- **Fits when:** API releases are frequent and reconnect storms hurt (§5.3).

#### E. A managed realtime layer (API Gateway WebSocket API, Ably, Pusher, Centrifugo)

*In the hexagon:* `RealtimeEmitterPort` gets an adapter that calls the vendor to push. Connection lifecycle moves out of `RawWebSocketHandler` into route handlers.

```
 clients ⇄ ┌──────────────────────────────┐   1 routes: $connect, subscribe,
           │ API Gateway (WebSocket API)  │     sendMessage, $disconnect
           │ owns the sockets             │─────────────────┐
           └──────────────────────────────┘                 ▼
                          ▲                          ┌────────────────┐
                          │ 3 POST @connections/     │ your backend   │──▶ PG
                          │   {connectionId}         │ (Lambda / HTTP)│
                          └──────────────────────────┤                │
                                                     └───────┬────────┘
                                                             │ 2 registry
                                                             ▼
                                        DynamoDB: connectionId → user, channels
```

- **Pros:** *(general)* the vendor scales the connections; no socket state in your JVM; fits an AWS shop.
- **Cons:** vendor lock-in; per-message and per-connection-minute cost; connection duration and idle limits; the backend must keep its own connection registry (DynamoDB) and post to each recipient; the biggest re-architecture.
- **Fits when:** you want to stop operating sockets entirely.

### Add-on

#### K. Transactional outbox plus relay

Upgrades B, C, I and J from "maybe delivered" to "delivered at least once".

```
 Task 1:  BEGIN
            INSERT message
            INSERT outbox(event)          same transaction
          COMMIT ──▶ PG
                      │  relay (poll, or CDC such as Debezium)
                      ▼
              publish to B / C / I / J ── retry until acked ── mark row sent
```

- **Pros:** an event is published if and only if the message committed, which closes the gap where a node dies between commit and publish; at-least-once; replayable.
- **Cons:** an extra table plus a relay process; polling adds latency and CDC adds operational weight; duplicates, so consumers dedupe by message id; outbox cleanup.
- **Fits when:** "saved but never broadcast" is unacceptable, for example notifications that must not be missed.

### Comparison

| Option | New infra | Protocol change | Delivery | Nodes that get each event | Complexity |
|---|---|---|---|---|---|
| A Harden single node | none | none | n/a | 1 | low |
| B Redis Pub/Sub | Redis | none | at-most-once | all | low |
| C PG LISTEN/NOTIFY | none | none | at-most-once | all | low |
| J SNS → SQS per node | SNS + SQS | none | at-least-once | all | medium |
| I Log (Kafka etc.) | log cluster | none | at-least-once | all | high |
| F Shared registry + routing | Redis | none | at-most-once | only interested | high |
| G Channel sharding | routing layer | client opens N sockets | n/a | 1 per channel | high |
| D STOMP + broker | broker | yes, all clients | broker-defined | broker routes | medium |
| H Gateway + bus | bus + 2nd service | none | bus-defined | gateways | high |
| E Managed | vendor service | yes, all clients | vendor-defined | vendor routes | high |
| K Outbox (add-on) | table + relay | none | at-least-once | as underlying | medium |

---

## 7. Recommended path

The recommendation follows from one fact (§5.6): there is no evidence yet that you need more than one task. So do the work that every option needs, and defer the choice that only one option needs.

### Stage 1, now: harden the single node (A)

| Change | Where |
|---|---|
| Wrap stored sessions in `ConcurrentWebSocketSessionDecorator` | 04 §2 |
| Clean up on every exit path (`handleTransportError` is not overridden). A reverse index `connectionToChannels` would make `removeConnection` O(1) instead of a scan over all channels, but is optional | 04 §3 |
| Catch per frame and reply `ERROR`, instead of closing the socket | 04 §7 |
| Put the persisted message id and `createdTime` in `OutboundWsEvent` | 04 §6 |
| Wire `UNSUBSCRIBE` end to end | 04 §8 |

The registry bugs found while testing it (the `null` return, the `unsubscribe` NPE, the missing connection cleanup and the inconsistent return contract) are already fixed in `InMemoryConnectionRegistryAdapter`.

**A caveat about 04 §3.** Its cleanup snippet drops empty sets with `computeIfPresent(... return set.isEmpty() ? null : set)`, while `subscribe` does `computeIfAbsent(...).add(...)`, which is two steps. Interleave them and a subscription is lost:

1. Thread S (`subscribe`): `computeIfAbsent` returns the existing set `X`.
2. Thread R (`removeConnection`): removes the last id from `X`, and `computeIfPresent` returns `null`, so `X` leaves the map.
3. Thread S: `X.add(c2)` succeeds on a set the map no longer holds.

`c2` is subscribed and never receives anything. Either keep empty sets (they are bounded by the number of channels), or make `subscribe` do the `add` inside `compute`, so both operations are atomic per key. This is 04 §1's own lesson applied to 04 §3. The current `removeConnection` takes the first option.

### Stage 2, when a second task is needed: a backplane with local registries

Default to **B** (Redis). Choose **J** if you would rather not run Redis, or **C** for two or three tasks and no new service.

The key refactor is the same for all three: move the fan-out loop out of `WsSendMessageService` to the delivery side.

```
 Task 1                                     Task 2  (same code)
┌───────────────────────────┐             ┌───────────────────────────┐
│ WsSendMessageService      │             │ BackplaneListener         │
│  1 save ──▶ PG            │             │   │ event                 │
│  2 broadcastToChannel     │             │   ▼                       │
│    (RealtimeEmitterPort)  │             │ DeliverToLocalConnections │
│       │                   │             │   (inbound use case)      │
│       ▼                   │             │   │ registry (local)      │
│ BackplaneAdapter ─────────┼─▶ backplane ┼──▶│ sessions (local)      │
└───────────────────────────┘  (B, C or J)└───────────────────────────┘
 every task also runs a listener, and skips originConnectionId when delivering
```

- The port becomes `RealtimeEmitterPort.broadcastToChannel(channelId, payload, originConnectionId)`. The loop over connections moves into a local-delivery use case that the backplane listener calls on every node.
- The payload carries `originConnectionId` (so the sender's own connection is still skipped) and the persisted message id (so clients can dedupe and order).
- At-most-once is acceptable **because** of §4: the database is the source of truth, and a client that reconnects catches up over the cursor endpoint.

### Later, only if the trigger in §8 appears

- **H** when API deploys keep dropping sockets.
- **K** when "saved but never broadcast" is unacceptable.
- **F, G, I** only at a scale this app has no evidence of. **D** or **E** only if you decide to change the protocol or stop running sockets.

---

## 8. Decision triggers

| If you see... | Then |
|---|---|
| You need a second ECS task, for availability or for CPU | Stage 2: B, or C or J (§7) |
| Sockets drop on every API deploy and users notice | H, or drain connections gracefully on deploy first *(general)* |
| One slow client delays other users' sends | Stage 1: the decorator's send limits (04 §2) |
| You need presence, typing indicators or offline push | Reconsider D, E or I |
| A saved message that never reached anyone is unacceptable | K on top of B, C or J |
| B's "every node gets every event" is measured as the bottleneck | F |
| The biggest channel's fan-out is slow | Stage 1 first (per-session send queues). A new topology does not shrink one channel's member count, and G makes a hot channel a hot node |

---

## 9. Where else this applies

The question underneath this chapter is: **what state lives inside one process, and what happens when there are two?** Ask it of anything that would be added later:

- **A rate limiter** kept in a `ConcurrentHashMap` limits each node separately, so the real limit is `N × limit`.
- **A cache** in the JVM serves stale data on the node that did not see the write.
- **A `@Scheduled` job** runs once per task, so with two tasks it runs twice.
- **Presence** ("who is online") is exactly the registry problem again.

Today the registry is the only such state (§4, last bullet). That is why one chapter can cover the whole scaling story.

### Checklist

- [ ] Every claim about multi-node behaviour names the state that is per-process
- [ ] `save` (commit) happens before any broadcast, on every path
- [ ] The outbound event carries an id the client can dedupe and order by
- [ ] The sender's connection is skipped by connection id, and that id survives the backplane
- [ ] A lossy push has a working catch-up path (the cursor endpoint), and clients use it on reconnect
- [ ] Each node cleans up its own connections on every exit path
- [ ] Anything chosen from §6 was checked against the docs, not against this chapter's *(general)* notes

---

## 10. Open questions

These change the recommendation. This repo cannot answer them.

1. **What is the client?** A browser or a native app decides how the handshake authenticates (§5.5).
2. **How many concurrent connections do you expect,** and how large is your biggest channel? (§5.6)
3. **How many ECS tasks run today, and is a second one planned?** If it is planned soon, Stage 2 stops being optional.
