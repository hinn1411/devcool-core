---
paths:
  - "src/main/java/**/websocket/**/*.java"
  - "src/main/java/**/realtime/**/*.java"
  - "frontend/src/ws/**/*"
---

# WebSocket protocol rules

The protocol is specified in `docs/plans/architecture/adr/0005-raw-websocket-protocol-v2.md` and `docs/plans/architecture/03-chat-system-design.md` §5. The `ws-protocol` skill has the full frame reference. Backend and frontend must change together.

- Envelope: `{v, type, reqId, channelId, payload}`. Each client frame with a `reqId` gets exactly one `ACK` or `NACK` carrying that `reqId`.
- A bad frame → `NACK`/`ERROR`. **Never close the socket for a frame-level error.** Close codes are reserved: 4401 auth, 4400 protocol, 1013 overload, 1012 restart.
- Order of operations when sending: **commit → ACK → publish**. Never broadcast something that isn't committed.
- Every message event carries `messageId`, `seq` and `createdTime`.
- The sender's own connection is skipped by `originConnectionId`; a node ignores its own backplane echo via `originNodeId`.
- Never do blocking I/O on a Redis/Lettuce callback thread. Hand off to the delivery executor.
- Sessions are wrapped in `ConcurrentWebSocketSessionDecorator`. Never call `session.sendMessage` on a raw session.
- `SUBSCRIBE`, `RESUME`, `TYPING`, `READ` and `ASK` all check channel membership in the application service.
- Per-process state (registry, subscriptions) must be cleaned up on **every** exit path: normal close, transport error, and server shutdown.
