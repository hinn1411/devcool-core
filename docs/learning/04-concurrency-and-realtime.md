# 04 — Concurrency & Realtime

HTTP gives you one thread, one request, one response, and a framework that cleans up after you. A WebSocket gives you none of that. Every habit that was safe in a controller has to be re-examined.

This chapter began as an audit of the first `/ws` code (`RawWebSocketHandler`, `WsSessionStore`, `WsRealtimeEmitterAdapter`, `Integer` ids). P3 fixed most of what it found. Each section keeps its lesson, shows the **current** code, and ends with a **Status** line: fixed (with the PR or task that fixed it) or open (with the P4 task that closes it). `file:line` references were re-read on 2026-11-26. Chapter 10 has the map of the current design.

---

## 1. A thread-safe collection does not make your code thread-safe

**The concept.** `ConcurrentHashMap` guarantees that each individual *method call* is atomic. It guarantees nothing about a *sequence* of calls. If you read, decide, then write, another thread can act between your read and your write — and the concurrent collection cannot help, because from its point of view those were two unrelated operations.

**In your code** — `adapters/out/realtime/immemory/InMemoryConnectionRegistryAdapter.java:12-13`:

```java
private final Map<String, Long> connectionToUser = new ConcurrentHashMap<>();
private final Map<Long, Set<String>> channelToConnections = new ConcurrentHashMap<>();
```

Both choices are correct, and `subscribe` uses the right idiom (`:37-41`):

```java
channelToConnections
    .computeIfAbsent(channelId, k -> ConcurrentHashMap.newKeySet())
    .add(connectionId);
```

`computeIfAbsent` is atomic, `newKeySet()` is concurrent. That is genuinely good code.

But the *sequences* are unguarded. `registerConnection` and `subscribe` are two separate calls with no atomic "is this connection still alive?" check between them. A `SUBSCRIBE` frame processed concurrently with `afterConnectionClosed` can re-add the dying connection to a channel set **after** cleanup has passed it. Nothing removes it again.

**The rule.** Picking the concurrent data structure is step one of about three. Ask separately: *which multi-step sequences must be atomic?* Those need `compute`, `merge`, a lock, or a redesign that makes the sequence a single operation.

**Status:** open. The race is narrow (a frame and a close for the same connection at once) and leaks one id per occurrence. P4 moves the registry to Valkey (ADR-0006), where the same question comes back as "which Redis commands must run in one `MULTI` or Lua script?".

---

## 2. `WebSocketSession` is not safe for concurrent writes

**This is the most serious realtime bug left in the codebase, and it is invisible under single-user testing.**

**The concept.** The WebSocket protocol forbids interleaved partial frame writes. Spring's `WebSocketSession` is a stateful, non-reentrant resource: two threads calling `sendMessage` on the same session corrupt the frame on the wire.

**In your code.** Every write goes through one method, `FrameWriter.send` (`adapters/in/websocket/session/FrameWriter.java:30-31`):

```java
public void send(WebSocketSession session, Object frame) throws IOException {
  session.sendMessage(new TextMessage(objectMapper.writeValueAsString(frame)));
}
```

It is called from two independent thread contexts:

- the `FrameAction`s and `ChatHandler` (ACK and ERROR replies), on **that session's own inbound thread**;
- `SessionEmitterAdapter.send` (`:34`), on **the sending user's thread**, during fan-out.

`SessionRegistry.java:11` stores the raw session:

```java
private final Map<String, WebSocketSession> sessions = new ConcurrentHashMap<>();
```

Nothing synchronises the writes. One writer method makes the fix easy, but it is not a fix by itself.

**Why it matters.** User A is mid-send (their handler writing an ACK on A's thread) while user B's broadcast writes to A's session on B's thread. You get `IllegalStateException: The remote endpoint was in state [TEXT_PARTIAL_WRITING]`. The emitter now logs it (§5), but A's partially written frame is still garbage. Two users in one channel is enough to trigger it.

Note the trap: the map is `ConcurrentHashMap`, so the *registry* is thread-safe. That makes the sessions inside it *look* protected. They aren't. This is §1 with real consequences.

**The fix.** Spring ships the answer. Wrap once at registration (`ChatHandler.afterConnectionEstablished`, `:57-61`):

```java
@Override
public void afterConnectionEstablished(WebSocketSession session) {
  sessionRegistry.put(
      new ConcurrentWebSocketSessionDecorator(session, SEND_TIME_LIMIT_MS, BUFFER_LIMIT_BYTES));
  ...
}
```

That also gives you the send-time and buffer limits you currently have nowhere. A slow mobile client presently blocks the *sender's* thread inside a synchronous `sendMessage`. Since P3 the sender's ACK is written before the fan-out (ch. 10 §3), but their next frame still waits.

**Status:** open. P4-T01 (sendTimeLimit 5 s, buffer 512 KB, `TERMINATE`). `.claude/rules/websocket-protocol.md` already forbids `sendMessage` on a raw session.

---

## 3. Lifecycle cleanup must mirror lifecycle setup — on every exit path

**The concept.** State registered in *N* places must be removed from *N* places, on *every* termination path. If your data structure makes that expensive, the structure is wrong.

**In your code.** Registration touches three structures:

```java
// ChatHandler.afterConnectionEstablished (:57-61)
sessionRegistry.put(session);                                       // 1
connections.connect(new ConnectCommand(connectionId, userId));      // 2 → connectionToUser
// later, on SUBSCRIBE (ConnectionService.subscribe)
connectionRegistryPort.subscribe(connectionId, channelId);          // 3 → channelToConnections
```

Cleanup — `ChatHandler.java:80-84` → `ConnectionService.disconnect` → `InMemoryConnectionRegistryAdapter.removeConnection` (`:21-29`):

```java
public void removeConnection(String connectionId) {
  if (connectionId == null) {
    return;
  }
  connectionToUser.remove(connectionId);
  for (Set<String> connections : channelToConnections.values()) {
    connections.remove(connectionId);
  }
}
```

**What the first version found.** `removeConnection` cleared only `connectionToUser`. `channelToConnections` was **never** cleaned, which caused two harms:

- *Unbounded memory growth.* Every connection that ever subscribed left a permanent `String` in that channel's set. A mobile client reconnecting every 30 seconds on flaky wifi added one dead id per reconnect, forever. These are fields of a singleton `@Component`: no TTL, no eviction, no size cap.
- *Broadcast amplification.* The fan-out loop iterated all those dead ids, each one a `sessions.get(...)` → `null` → early return. After a month, a 3-person channel would broadcast to thousands of dead ids per message, and send latency would degrade linearly and invisibly.

**Today** the loop above removes the id from every channel set, so both harms are gone. It is a scan over all channels, because there is still no reverse index (connection → channels). A reverse index makes it O(1):

```java
private final Map<String, Set<Long>> connectionToChannels = new ConcurrentHashMap<>();

public void removeConnection(String connectionId) {
  connectionToUser.remove(connectionId);
  Set<Long> channels = connectionToChannels.remove(connectionId);
  if (channels != null) {
    channels.forEach(ch -> channelToConnections.computeIfPresent(ch, (k, set) -> {
      set.remove(connectionId);
      return set;   // keep empty sets: see below
    }));
  }
}
```

The first version of this snippet returned `set.isEmpty() ? null : set` to drop empty sets. That races with `subscribe`'s two-step `computeIfAbsent(...).add(...)` and loses a subscription (ch. 10 §7 walks through the interleaving). Keeping empty sets, as the current code does, avoids that. They are bounded by the number of channels.

**A new exit path since P3-T16.** A connection can now leave a channel without closing: remove-member and leave call `unsubscribeUser(userId, channelId)` **after commit** (`MemberManagementService:124-138`). It drops every connection of that user from that channel. After commit, because a rollback must not cut off someone who is still a member.

`handleTransportError` is still not overridden in `ChatHandler`. `MdcHandlerDecorator` only wraps it with MDC fields. Cleanup relies on Spring calling `afterConnectionClosed` after a transport error *(general: verify for your container)*.

**Where else this applies.** Any long-lived in-memory map keyed by an ephemeral identifier. Ask: *what removes this entry, and does that run on every path including crashes?* If the answer requires a scan, add the index first.

**Status:** the leak is fixed (#85). The reverse index and an explicit `handleTransportError` are still open (P4-T01, which requires cleanup "on every exit path").

---

## 4. Never return `null` for a collection

**The concept.** *Effective Java* Item 54. A `null` return forces every caller, forever, to write a guard — and the cost is paid at the one call site that forgets.

**What the first version found.** `getConnectionsByChannel` returned `channelToConnections.get(channelId)`, which is `null` for a channel nobody had subscribed to, and the fan-out loop didn't guard it. A user who sent `SEND_MESSAGE` to a channel *without* first sending `SUBSCRIBE` got the message persisted and **committed**, then an NPE on the null set. The exception escaped the handler, Spring closed the socket, and the client never got an ACK, even though the message *was* in the database. A client retry double-posted it. One missing `Collections.emptySet()` produced duplicate messages.

The sibling bug: `unsubscribe` checked `!connectionSet.isEmpty()` instead of `!= null`, an NPE when the key was absent. The author sensed something could go wrong and guarded the wrong property.

**Today** — `InMemoryConnectionRegistryAdapter.java:44-62`:

```java
public void unsubscribe(String connectionId, Long channelId) {
  Set<String> connections = channelToConnections.get(channelId);
  if (connections != null) {
    connections.remove(connectionId);
  }
}

public Set<String> getConnectionsByChannel(Long channelId) {
  return Set.copyOf(channelToConnections.getOrDefault(channelId, Set.of()));
}
```

`getOrDefault` removes the null. `Set.copyOf` also gives the caller an immutable snapshot, so the fan-out loop never iterates a set another thread is changing. The duplicate-post path is now closed twice over: P3-T03 made `SEND_MESSAGE` idempotent by `clientMsgId`, so a retry returns the original message instead of inserting a second one.

**Where else this applies.**
- `ContentType.fromCode`, `ChannelType.fromCode` and `BoundaryType.fromCode` still `return null` for an unknown code (for example `ContentType.java:19-25`). A domain factory that returns null pushes the failure arbitrarily far from its cause. **Open.**
- `TokenIssuerPort.verifyRefresh` now returns `Optional<TokenSubject>` (`TokenIssuerAdapter:102`), and `RefreshTokenService:82` handles the empty case. The private `verify` helper still returns `null`, but it never leaves the adapter. **Fixed.**

**Status:** the registry is fixed (#85). The enum `fromCode` factories are open.

---

## 5. `catch (Exception ignored)` collapses "recoverable" and "impossible"

**What the first version found** — `WsRealtimeEmitterAdapter`:

```java
try {
  String json = objectMapper.writeValueAsString(payload);
  session.sendMessage(new TextMessage(json));
} catch (Exception ignored) {
  log.info("Ignore emitting response exception!");
}
```

Three mistakes stacked:

1. One `catch` covered `IOException` (transport dead — recoverable, should trigger cleanup), `JsonProcessingException` (a *programming bug* — should never be swallowed) and `IllegalStateException` (the concurrent write from §2). The handler was written for the first case only.
2. The variable was named `ignored`.
3. `log.info` with no throwable, no `connectionId`, no payload — the stack trace was destroyed.

A serialization bug in the push payload meant **nobody in any channel ever received a message**, and the only evidence was `INFO Ignore emitting response exception!` with nothing to grep for.

**Today** — `adapters/in/websocket/session/SessionEmitterAdapter.java:28-40`:

```java
WebSocketSession session = sessionRegistry.get(connectionId);
if (Objects.isNull(session) || !session.isOpen()) {
  return;
}
try {
  writer.send(session, MessagePush.from(event));
} catch (IOException | IllegalStateException ex) {
  // At-most-once: the client catches up by seq.
  log.warn(
      "Push of message {} to {} failed: {}", event.messageId(), connectionId, ex.toString());
}
```

Better on every count: a named multi-catch, `WARN`, and a log line with the message id and connection id. Three things are still missing:

- **`JsonProcessingException` is a subclass of `IOException`.** A serialization bug in `MessagePush` is still caught here and logged once per recipient as if the transport had failed. The multi-catch *looks* like it excludes programming bugs, and it doesn't. Serialise once, before the loop, and let a failure throw.
- **`ex.toString()` is not the throwable.** The log has the class and message but no stack trace. Pass `ex` as the last argument instead.
- **No eviction.** A failed write to a dead transport is exactly when the connection should leave the registry (§3), and it doesn't.

**The fix.**
```java
} catch (IOException e) {   // after serialising outside the loop, this really is transport
  log.warn("Push of message {} to {} failed, evicting", event.messageId(), connectionId, e);
  connections.disconnect(new DisconnectCommand(connectionId));
}
```

**Status:** partly fixed (#199). The `JsonProcessingException` trap, the missing stack trace and the eviction are open. The decorator from P4-T01 changes this code anyway, since `TERMINATE` closes slow sessions itself.

---

## 6. Delivery semantics are chosen, not inherited

**What the first version found.** The fan-out loop always "succeeded" because §5 swallowed everything. Recipients 1-3 got the message, recipient 4's socket was half-closed and lost it, recipients 5-8 got it. Nobody — not the sender, not the server, not recipient 4 — ever learned. That's **at-most-once with silent loss**, the weakest possible guarantee, arrived at by default rather than by decision. The payload (`OutboundWsEvent`) had no message id, no sequence and no timestamp, so a client could not reorder, deduplicate or detect a gap. Yet every piece of a better guarantee already existed: `save` returned the id (and it was discarded), `clientMsgId` was echoed but never used, and the cursor endpoint worked.

**Today** — `application/service/chat/SendMessageService.java:30-50`:

```java
public void sendMessage(SendMessageCommand command, Consumer<SavedMessage> onCommitted) {
  SavedMessage saved = saveMessageUseCase.save(toCreateCommand(command));
  try {
    onCommitted.accept(saved);                  // the ACK
  } finally {
    // A failed ACK still pushes; a retry was already pushed.
    if (!saved.duplicate()) {
      publish(command, saved);
    }
  }
}
```

The pieces are now connected:

- **The guarantee is a decision.** `RealtimeEmitterPort` is documented as "At-most-once push (ADR-0006)", and the emitter's comment says how a client recovers: "the client catches up by seq".
- **Events can be ordered, deduplicated and gap-checked.** `MessageEvent` and `MessagePush` carry `messageId`, a per-channel `seq` (ADR-0012) and `createdTime`, and the SEND ACK carries the same three (P3-T04).
- **The write path is idempotent.** `clientMsgId` is a unique key per sender. A retry returns the original `SavedMessage` with `duplicate = true`, gets the same ACK, and is not pushed a second time (P3-T03).
- **Order of operations:** commit → ACK → publish.

At-most-once *push* plus a gap-detectable `seq` plus a catch-up read is an honest design. Push is an optimisation; the database is the delivery guarantee.

**Status:** fixed (P3-T03, P3-T04). The client side, `RESUME{lastSeq}` and `RESYNC_REQUIRED`, is P4 (ADR-0005).

---

## 7. A per-frame error must not be a connection-level error

**The concept.** HTTP has `@RestControllerAdvice` to turn an exception into a status code. **That is a Spring MVC construct — it does not apply to `WebSocketHandler`.** A long-lived connection needs its own error-mapping layer, and nothing provides one for free.

**What the first version found.** The handler wrapped only JSON parsing in try/catch. A client that subscribed to a channel it had been removed from threw `MemberNotFoundException` out of `handleTextMessage`, and Spring closed the session with `SERVER_ERROR`. The user was disconnected from **all** channels because of one bad frame, with no explanation.

**Today** — `ChatHandler.dispatch` (`:87-98`):

```java
FrameAction action = actions.get(frame.action());
if (Objects.isNull(action)) {
  writer.error(session, frame.clientMsgId(), "Unsupported action");
  return;
}
try {
  action.handle(session, frame);
} catch (DomainException ex) {
  writer.error(session, frame.clientMsgId(), ex.getErrorCode().code());
}
```

The removed-member case now gets an `ERROR` frame with the error code, and the socket stays open. But only `DomainException` is caught. Anything else still escapes, and Spring's `ExceptionWebSocketHandlerDecorator` closes the socket with 1011. One concrete path: when the SEND ACK write fails, `SendMessageAction:64` wraps the `IOException` in `UncheckedIOException`. A DB timeout or a bug in a mapper would do the same.

**The fix.** The missing branch, mirroring `ApiExceptionHandler`'s fallback:

```java
} catch (DomainException ex) {
  writer.error(session, frame.clientMsgId(), ex.getErrorCode().code());
} catch (RuntimeException ex) {
  log.error("Unhandled frame error", ex);   // MDC already has userId and connectionId
  writer.error(session, frame.clientMsgId(), "INTERNAL");
}
```

**Status:** partly fixed (#199). The catch-all is P4-T02, where ADR-0005 rule 2 ("a frame error never closes the socket") becomes part of the protocol contract.

---

## 8. A protocol case that exists in every layer but the entry point

**What the first version found.** `WsMessageType` declared `UNSUBSCRIBE`, and `WsUnsubscribeUseCase` existed, but `WsUnsubscribeService.unsubscribe` was an empty method. Its `UnsubscribeCommand` was `record UnsubscribeCommand() {}`, with no `connectionId` and no `channelId`, so it could not have worked even if wired. And the handler's `switch` had no case for it, so `UNSUBSCRIBE` fell through to `"Unsupported action"`. Combined with §3, clients had no way to unsubscribe even deliberately. The same defect class existed in message types: `ContentType` had four constants and two strategies (`TEXT`, `IMAGE`), so `MARKDOWN` and `VIDEO` failed at request time.

**Today** both are fixed:
- `UnsubscribeAction` → `ChannelSubscriptionUseCase.unsubscribe(UnsubscribeCommand(connectionId, channelId))` → `registry.unsubscribe` (P3-T16).
- All four content types have a strategy: `Text`, `Markdown`, `Image` and `VideoMessageCreationStrategy` (P3-T15).

**The lesson still holds.** **"The DI container found N beans" is not "all cases are covered."** A registry built by iterating injected beans is a runtime construct; enum coverage is a compile-time fact. The current code has two such registries and neither asserts coverage:

- `MessageService` builds `EnumMap<ContentType, MessageCreationStrategy>` from the injected beans, and a missing type fails **per request** in `strategyFor` (`:191-198`).
- `ChatHandler` builds `EnumMap<FrameType, FrameAction>`. It rejects *duplicates* at startup (`:49-53`), but not gaps. `FrameType.ACK` and `ERROR` are server-only, so a client that sends them gets an explicit `"Unsupported action"`. That's acceptable only because it is deliberate; ADR-0005's v2 splits client and server types.

Assert coverage at startup:

```java
Set<ContentType> missing = EnumSet.complementOf(EnumSet.copyOf(creationStrategies.keySet()));
if (!missing.isEmpty()) {
  throw new IllegalStateException("No message strategy for: " + missing);
}
```

Fail at boot, not at the first unlucky request.

**Status:** both gaps are fixed (P3-T15, P3-T16). The startup assertion is open (nice to have).

---

## 9. Two smaller things

**`WsSessionStore.sessions` was `public`.** `final` protects the reference, not the contents, so any injected consumer could call `.clear()`. SpotBugs flags this (`PA_PUBLIC_MUTABLE_OBJECT_ATTRIBUTE`); see chapter 06. **Fixed (#199):** `SessionRegistry.sessions` is `private`, and the class exposes only `put`, `get` and `remove`.

**Handshake auth uses `Optional.of` on a nullable value** — `AuthHandshakeInterceptor.java:32-38`:

```java
Optional.of(SecurityContextHolder.getContext().getAuthentication())
    .filter(Authentication::isAuthenticated)
    ...
    .orElse(null);
```

`getAuthentication()` returns `null` when the context is empty, and `JwtAuthFilter` calls `clearContext()` on all three failure paths (`:35,44,53`). `Optional.of(null)` throws NPE. The expression is self-contradictory: `of` asserts non-null, while `.orElse(null)` handles the null case. In practice the NPE is unlikely *(general)*: Spring Security's anonymous filter is not disabled in `SecurityConfig`, so an unauthenticated handshake usually arrives with an `AnonymousAuthenticationToken`, whose `"anonymousUser"` principal the `Long` filter rejects. That is luck, not design. Use `ofNullable`. **Open.** The handshake is rewritten for tickets in P4-T06 (ADR-0011), which is the natural moment to fix it.

---

## Checklist

- [ ] Every multi-step sequence on a shared map is atomic, not just each call (§1, open)
- [ ] `WebSocketSession` is wrapped in `ConcurrentWebSocketSessionDecorator` before storage (§2, P4-T01)
- [ ] Every registration has a matching removal on every exit path, including `handleTransportError` (§3: close and remove/leave done; transport error open)
- [x] No method returns `null` for a collection (§4)
- [ ] No `catch` that discards the throwable or hides a programming bug (§5: `ex.toString()` and `JsonProcessingException` open)
- [x] The broadcast payload carries a message id (§6: `messageId`, `seq`, `createdTime`)
- [ ] Per-frame errors produce an ERROR frame, not a socket close (§7: domain errors done; others P4-T02)
- [x] Every enum constant in a protocol has an implementation or an explicit, tested rejection (§8; a startup assertion would make it permanent)
- [x] Side effects on non-transactional resources happen after commit (`unsubscribeUser` in `afterCommit`; ACK and push after `save` commits)
