# 04 — Concurrency & Realtime

HTTP gives you one thread, one request, one response, and a framework that cleans up after you. A WebSocket gives you none of that. Every habit that was safe in a controller has to be re-examined.

---

## 1. A thread-safe collection does not make your code thread-safe

**The concept.** `ConcurrentHashMap` guarantees that each individual *method call* is atomic. It guarantees nothing about a *sequence* of calls. If you read, decide, then write, another thread can act between your read and your write — and the concurrent collection cannot help, because from its point of view those were two unrelated operations.

**In your code** — `adapters/out/realtime/immemory/InMemoryConnectionRegistryAdapter.java:11-12`:

```java
private final Map<String, Integer> connectionToUser = new ConcurrentHashMap<>();
private final Map<Integer, Set<String>> channelToConnections = new ConcurrentHashMap<>();
```

Both choices are correct, and `subscribe` uses the right idiom:

```java
channelToConnections
    .computeIfAbsent(channelId, k -> ConcurrentHashMap.newKeySet())
    .add(connectionId);
```

`computeIfAbsent` is atomic, `newKeySet()` is concurrent. That is genuinely good code.

But the *sequences* are unguarded. `registerConnection` and `subscribe` are two separate calls with no atomic "is this connection still alive?" check between them. A `SUBSCRIBE` frame processed concurrently with `afterConnectionClosed` re-adds the dying connection to a channel set **after** cleanup ran — permanently, given §3.

**The rule.** Picking the concurrent data structure is step one of about three. Ask separately: *which multi-step sequences must be atomic?* Those need `compute`, `merge`, a lock, or a redesign that makes the sequence a single operation.

---

## 2. `WebSocketSession` is not safe for concurrent writes

**This is the most serious realtime bug in the codebase, and it is invisible under single-user testing.**

**The concept.** The WebSocket protocol forbids interleaved partial frame writes. Spring's `WebSocketSession` is a stateful, non-reentrant resource: two threads calling `sendMessage` on the same session corrupt the frame on the wire.

**In your code.** `session.sendMessage(...)` is called from two independent thread contexts:

- `RawWebSocketHandler` lines 50, 58, 68, 76, 92, 98 — that session's own inbound thread
- `WsRealtimeEmitterAdapter.java:31` — the *sending user's* thread, during fan-out

`WsSessionStore.java:11` stores the raw session:

```java
public final Map<String, WebSocketSession> sessions = new ConcurrentHashMap<>();
```

Nothing synchronises them.

**Why it matters.** User A is mid-send (their handler writing an ACK on A's thread) while user B's broadcast writes to A's session on B's thread. You get `IllegalStateException: The remote endpoint was in state [TEXT_PARTIAL_WRITING]` — and thanks to §5 it is swallowed — while A's partially-written frame is now garbage. Two users in one channel is enough to trigger it.

Note the trap: the map is `ConcurrentHashMap`, so the *registry* is thread-safe. That makes the sessions inside it *look* protected. They aren't. This is §1 with real consequences.

**The fix.** Spring ships the answer — wrap once at registration:

```java
@Override
public void afterConnectionEstablished(WebSocketSession session) {
  sessionStore.put(new ConcurrentWebSocketSessionDecorator(session, SEND_TIME_LIMIT_MS, BUFFER_LIMIT_BYTES));
  ...
}
```

That also gives you the send-time and buffer limits you currently have nowhere — a slow mobile client presently blocks the *sender's* thread inside a synchronous `sendMessage`, degrading everyone in the channel.

---

## 3. Lifecycle cleanup must mirror lifecycle setup — on every exit path

**The concept.** State registered in *N* places must be removed from *N* places, on *every* termination path. If your data structure makes that expensive, the structure is wrong.

**In your code.** Registration touches three structures:

```java
// RawWebSocketHandler.afterConnectionEstablished
sessionStore.put(session);                                   // 1
connectionRegistryPort.registerConnection(connectionId, userId); // 2  → connectionToUser
// later, on SUBSCRIBE
connectionRegistryPort.subscribe(connectionId, channelId);   // 3  → channelToConnections
```

Cleanup touches two — `RawWebSocketHandler.java:107-112`:

```java
@Override
public void afterConnectionClosed(WebSocketSession session, CloseStatus status) {
  String connectionId = session.getId();
  sessionStore.remove(connectionId);
  connectionRegistryPort.removeConnection(connectionId);   // only clears connectionToUser
}
```

`channelToConnections` is **never** cleaned. And there is no reverse index (connection → channels), so a correct cleanup would require scanning every channel's set.

`handleTransportError` is not overridden at all, so abrupt failures skip even this.

**Why it matters — two distinct harms.**

*Unbounded memory growth.* Every connection that ever subscribes leaves a permanent `String` in that channel's set. A mobile client reconnecting every 30 seconds on flaky wifi adds one dead id per reconnect, forever. These are `final` fields of a singleton `@Component` — no TTL, no eviction, no size cap.

*Broadcast amplification.* `WsSendMessageService`'s fan-out loop iterates all those dead ids, calling `sessionStore.get(...)` → `null` → early return. After a month, a 3-person channel broadcasts to thousands of dead ids per message. Send latency degrades linearly and invisibly.

**The fix.** Add the reverse index that makes cleanup O(1):

```java
private final Map<String, Set<Integer>> connectionToChannels = new ConcurrentHashMap<>();

public void removeConnection(String connectionId) {
  connectionToUser.remove(connectionId);
  Set<Integer> channels = connectionToChannels.remove(connectionId);
  if (channels != null) {
    channels.forEach(ch -> channelToConnections.computeIfPresent(ch, (k, set) -> {
      set.remove(connectionId);
      return set.isEmpty() ? null : set;   // also prevents empty-set accumulation
    }));
  }
}
```

**Where else this applies.** Any long-lived in-memory map keyed by an ephemeral identifier. Ask: *what removes this entry, and does that run on every path including crashes?* If the answer requires a scan, add the index first.

---

## 4. Never return `null` for a collection

**The concept.** *Effective Java* Item 54. A `null` return forces every caller, forever, to write a guard — and the cost is paid at the one call site that forgets.

**In your code** — `InMemoryConnectionRegistryAdapter.java:44-47`:

```java
@Override
public Set<String> getConnectionsByChannel(Integer channelId) {
  return channelToConnections.get(channelId);
}
```

`Map.get` returns `null` for an absent key. And the caller doesn't guard — `WsSendMessageService.java:32,43`:

```java
Set<String> connections = connectionRegistryPort.getConnectionsByChannel(command.channelId());
...
for (String connectionId : connections) {
```

**Why it matters — fully reproducible.** A user opens a socket and sends `SEND_MESSAGE` to a channel *without* first sending `SUBSCRIBE`. The message is persisted and **committed** (line 30), then line 43 throws NPE on a null set. The exception escapes the handler, Spring closes the socket, the client never gets an ACK — and the message *is* in the database. A client retry double-posts it.

So this single missing `Collections.emptySet()` produces duplicate messages.

**The sibling bug** — the same file, lines 36-42:

```java
public void unsubscribe(String connectionId, Integer channelId) {
  Set<String> connectionSet = channelToConnections.get(channelId);
  if (!connectionSet.isEmpty()) {     // NPE when the key is absent
    connectionSet.remove(connectionId);
  }
}
```

A null check was needed; an emptiness check was written. And the emptiness check is pointless anyway — removing from an empty set is a harmless no-op. The tell is that the author sensed something could go wrong here and guarded the wrong property.

**The fix.** `getOrDefault(channelId, Set.of())` for reads, `computeIfPresent` for removal.

**Where else this applies.** `ContentType.fromCode`, `ChannelType.fromCode` and `BoundaryType.fromCode` all `return null` for an unknown code — a domain factory returning null pushes the failure arbitrarily far from its cause. `TokenIssuerAdapter.verifyRefresh` returns `null` on verification failure and `RefreshTokenService:36` dereferences it immediately. Same class, security-critical.

---

## 5. `catch (Exception ignored)` collapses "recoverable" and "impossible"

**In your code** — `adapters/out/realtime/immemory/WsRealtimeEmitterAdapter.java:29-34`:

```java
try {
  String json = objectMapper.writeValueAsString(payload);
  session.sendMessage(new TextMessage(json));
} catch (Exception ignored) {
  log.info("Ignore emitting response exception!");
}
```

Three mistakes stacked:

1. One `catch` covers `IOException` (transport dead — recoverable, should trigger cleanup), `JsonProcessingException` (a *programming bug* — should never be swallowed) and `IllegalStateException` (the concurrent write from §2). The handler is written for the first case only.
2. The variable is named `ignored`.
3. `log.info` with no throwable, no `connectionId`, no payload — the stack trace is destroyed.

**Why it matters.** A serialization bug in `OutboundWsEvent` means **nobody in any channel ever receives a message**, and your only evidence is `INFO Ignore emitting response exception!` with nothing to grep for.

This is also exactly where a dead session *should* be evicted from the registry, fixing §3 — instead it's where the signal is destroyed.

**The fix.**
```java
} catch (IOException e) {
  log.warn("Emit failed, evicting connection {}", connectionId, e);
  connectionRegistry.removeConnection(connectionId);
} catch (JsonProcessingException e) {
  throw new IllegalStateException("Unserialisable payload: " + payload.getClass(), e);
}
```

---

## 6. Delivery semantics are chosen, not inherited

**In your code** — `WsSendMessageService.java:42-47`:

```java
String currentConnectionId = command.connectionId();
for (String connectionId : connections) {
  if (!Objects.equals(currentConnectionId, connectionId)) {
    realtimeEmitterPort.sendMessageToConnection(connectionId, payload);
  }
}
```

Because §5 swallows everything, this loop always "succeeds". Recipients 1-3 get the message, recipient 4's socket is half-closed and loses it, recipients 5-8 get it. Nobody — not the sender, not the server, not recipient 4 — ever learns. Recipient 4's UI is permanently missing a message.

That's **at-most-once with silent loss**, the weakest possible guarantee, arrived at by default rather than by decision.

**What's missing, and what you already have.** The payload is:

```java
public record OutboundWsEvent(
    String type, Integer channelId, Integer senderId, String contentType, String content) {}
```

No message id, no sequence, no timestamp. The client cannot reorder, deduplicate, or detect a gap.

But look at what already exists: `saveMessageUseCase.save(saveCommand)` at line 30 **returns the message id** and the return value is discarded. `WsClientFrame.java:10` carries `String clientMsgId // Idempotent key`, which is echoed in the ACK and never used for idempotency. And `GET /api/v1/channels/{id}/messages?cursorId=` is built and working.

Every piece of at-least-once delivery is already in the codebase. None of it is connected.

**The fix.** Include the DB-assigned id in the payload; have the client reconcile via the cursor endpoint on reconnect; use `clientMsgId` to deduplicate on the write path.

---

## 7. A per-frame error must not be a connection-level error

**The concept.** HTTP has `@RestControllerAdvice` to turn an exception into a status code. **That is a Spring MVC construct — it does not apply to `WebSocketHandler`.** A long-lived connection needs its own error-mapping layer, and nothing provides one for free.

**In your code.** `RawWebSocketHandler.handleTextMessage` wraps only JSON parsing in try/catch (lines 47-55). Every use-case call is unguarded:

```java
case WsMessageType.SUBSCRIBE -> {
  subscribeUseCase.subscribe(new SubscribeCommand(connectionId, userId, clientFrame.channelId()));
  ...
}
```

**Why it matters.** A client subscribes to a channel it was removed from → `MemberNotFoundException` propagates out of `handleTextMessage` → Spring closes the session with `SERVER_ERROR`. The user is disconnected from **all** channels because of one bad frame, with no explanation.

The protocol already has the right vocabulary — `WsServerFrame(WsMessageType.ERROR, ...)` — used only for malformed JSON and unknown actions.

**The fix.** A per-frame catch that mirrors `ApiExceptionHandler`:

```java
try {
  dispatch(session, clientFrame, connectionId, userId);
} catch (DomainException e) {
  send(session, new WsServerFrame(ERROR, clientFrame.clientMsgId(), e.getErrorCode().code()));
} catch (Exception e) {
  log.error("Unhandled frame error on {}", connectionId, e);
  send(session, new WsServerFrame(ERROR, clientFrame.clientMsgId(), "INTERNAL"));
}
```

---

## 8. A protocol case that exists in every layer but the entry point

`WsMessageType.java:5` declares `UNSUBSCRIBE`. `WsUnsubscribeUseCase` exists. `WsUnsubscribeService` implements it:

```java
public class WsUnsubscribeService implements WsUnsubscribeUseCase {
  @Override
  public void unsubscribe(UnsubscribeCommand command) {}
}
```

`UnsubscribeCommand` is `public record UnsubscribeCommand() {}` — no `connectionId`, no `channelId`, so it could not work even if wired.

And `RawWebSocketHandler`'s switch has **no case for it**. `UNSUBSCRIBE` falls to `default -> "Unsupported action"`. Nothing in `src/main` injects the use case.

`CLAUDE.md` documents the protocol as "Subscribe, Unsubscribe, SendMessage".

**Why it matters.** Combined with §3, clients have no way to unsubscribe even deliberately — which is why the leak has no workaround.

**Where else this applies.** This is the same defect class as `ContentType.VIDEO` having no strategy. And it's worse than documented: `ContentType` has four constants (`TEXT`, `MARKDOWN`, `IMAGE`, `VIDEO`) and exactly **two** strategies are registered (`TEXT`, `IMAGE`). **`MARKDOWN` is also unimplemented** — so it's 2 of 4, not 1 of 4.

The general rule: **"the DI container found N beans" is not "all cases are covered."** A registry built by iterating injected beans is a runtime construct; enum coverage is a compile-time fact. Assert it at startup:

```java
Set<ContentType> missing = EnumSet.complementOf(EnumSet.copyOf(creationStrategies.keySet()));
if (!missing.isEmpty()) {
  throw new IllegalStateException("No message strategy for: " + missing);
}
```

Fail at boot, not at the first unlucky request.

---

## 9. Two smaller things

**`WsSessionStore.sessions` is `public`.** `final` protects the reference, not the contents — any injected consumer can call `.clear()`. The class already has `put`/`get`/`remove`. SpotBugs flags this (`PA_PUBLIC_MUTABLE_OBJECT_ATTRIBUTE`) and nobody saw it; see chapter 06.

**Handshake auth uses `Optional.of` on a nullable value** — `WsAuthHandShakeInterceptor.java:33-39`:

```java
Optional.of(SecurityContextHolder.getContext().getAuthentication())
    ...
    .orElse(null);
```

`getAuthentication()` returns `null` when the context is empty, and `JwtAuthFilter` calls `clearContext()` on all three failure paths. `Optional.of(null)` throws NPE. The expression is self-contradictory: `of` asserts non-null, `.orElse(null)` handles the null case. Use `ofNullable`.

---

## Checklist

- [ ] Every multi-step sequence on a shared map is atomic, not just each call
- [ ] `WebSocketSession` is wrapped in `ConcurrentWebSocketSessionDecorator` before storage
- [ ] Every registration has a matching removal on every exit path, including `handleTransportError`
- [ ] No method returns `null` for a collection
- [ ] No `catch (Exception)` that discards the throwable
- [ ] The broadcast payload carries a message id
- [ ] Per-frame errors produce an ERROR frame, not a socket close
- [ ] Every enum constant in a protocol has an implementation or an explicit, tested rejection
- [ ] Side effects on non-transactional resources happen after commit
