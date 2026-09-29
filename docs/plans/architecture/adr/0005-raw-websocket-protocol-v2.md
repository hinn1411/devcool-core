# 0005 — Keep raw WebSocket; typed protocol v2

- **Status:** Accepted
- **Date:** 2026-09-29
- **Phase:** P4

## Context
`RawWebSocketHandler` handles `WsClientFrame{action, channelId, contentType, content, clientMsgId}` with actions SUBSCRIBE / SEND_MESSAGE / PING. It has no request ids for most actions and no message id or seq in pushes. UNSUBSCRIBE is unhandled, and any exception closes the socket. The roadmap adds edit/delete, typing, presence, receipts, resume and AI streaming.

## Options considered

### A — Raw WebSocket with a versioned, typed envelope
- Pros: evolves the existing code. One socket carries chat, presence and AI streams. Full control over ACK/resume semantics. A browser `WebSocket` needs no library.
- Cons: you implement heartbeat, ACK, resume and error frames yourself.

### B — STOMP over WebSocket (Spring's `@MessageMapping`, simple broker or broker relay)
- Pros: destinations, subscriptions and the Spring programming model come built in. A broker relay (RabbitMQ) gives multi-node fan-out.
- Cons: rewrites the handler and the client (stomp.js). The relay needs RabbitMQ. It doesn't give ordering, dedupe or resume anyway.

### C — SSE for server → client + REST POST for client → server
- Pros: plain HTTP, auto-reconnect with `Last-Event-ID` built in, easy through proxies.
- Cons: two channels. Typing and read events become HTTP requests. Not the "chat system" answer interviewers expect.

### D — Socket.IO / a managed service (Ably, Pusher, API Gateway WS)
- Pros: rooms, reconnection and fallbacks for free.
- Cons: no first-class Java server (Socket.IO), or you outsource the interesting part.

## Decision
**A.** Envelope:

```json
{ "v": 2, "type": "<TYPE>", "reqId": "<client-chosen>", "channelId": 12, "payload": { } }
```

The full type list is in [03 §5](../03-chat-system-design.md#5-the-websocket-protocol). Rules:
1. Each client frame with a `reqId` gets exactly one `ACK` or `NACK` with that `reqId`.
2. A frame error never closes the socket. Close codes are reserved for auth failure (4401), protocol version mismatch (4400), overload (1013) and restart (1012).
3. Every message event carries `messageId`, `seq` and `createdTime`.
4. Unknown `type` → `NACK{code: UNSUPPORTED}`. Unknown fields are ignored, so it's forward compatible.
5. Missing `v` or `v: 1` → the server accepts the legacy frames during P4 only, then removes them.

## Consequences
- The protocol reference lives in the `ws-protocol` skill and `.claude/rules/websocket-protocol.md`, so backend and frontend stay in sync.
- Frame DTOs live in `adapters/in/websocket/dto`. Domain commands stay transport-free.

## Revisit when
- You need a non-browser client with different transport needs, or a managed realtime layer becomes cheaper than running sockets.
