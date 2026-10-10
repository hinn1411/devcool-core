# Improvements — WebSocket `SEND_MESSAGE` has no membership check

**Date:** 2026-10-10
**Found on:** `feat/p1-t16-virtual-threads` (while writing `WsVirtualThreadsIT`)
**Path:** WS `/ws` → `RawWebSocketHandler` → `WsSendMessageService.sendMessage`
**Severity:** High (authenticated is not authorized)

---

## 1. Any signed-in user can post into any channel
**File:** `src/main/java/com/devcool/application/service/chat/WsSendMessageService.java:26-30`

`sendMessage` saves the message and fans it out without checking that the caller is a member of `channelId` (the code only says `// Validate channel status later`). `MessageService.save` doesn't check it either. `SUBSCRIBE` does check it (`WsSubscribeService`), so the gap is only on the write.

**Scenario:** user A has a valid JWT and belongs to no channels. A opens `/ws` and sends `{"action":"SEND_MESSAGE","channelId":42,"contentType":"TEXT","content":"hi"}`. The message is saved in channel 42, every subscriber gets it, and A receives `ACK SENT`.

**Breaks:** [03 §12](../plans/architecture/03-chat-system-design.md#12-security-specifics) ("every channel read/write path checks membership in the service") and the rule in [lessons.md](lessons.md). It's the same class of bug as audit items #4 and #5 (P1-T06), on the WS side.

**Fix (recommended):** in `WsSendMessageService.sendMessage`, before `save`, do the same check as `WsSubscribeService`:
- `channelPort.existById(channelId)`, or else throw `ChannelNotFoundException`.
- `memberPort.existMemberOfChannelByUserId(channelId, userId)`, or else throw `MemberNotFoundException`.

Do the check in the service, not in the handler (hexagonal rule). The service also needs `ChannelPort` and `MemberPort` injected.

**Error to the client:** today any exception from a use case escapes `handleTextMessage` and closes the socket (ADR-0005, context). Until protocol v2 adds error frames, closing the socket is acceptable. The key point is that nothing is saved and nothing is fanned out.

**Tests:**
- Unit (`WsSendMessageServiceTest`, Mockito): non-member → throws `MemberNotFoundException`, `save` and the emitter are never called. Unknown channel → `ChannelNotFoundException`. Member → saved and fanned out.
- IT (reuse the harness in `WsVirtualThreadsIT`): a non-member sends → no new `message` row and the subscriber receives nothing.

**Owner:** not covered by any phase task yet. Options:
- Add it to P1 as a new task, since P1's goal is "the open security holes closed".
- Or fold it into P3-T03, which rewrites `MessageService.save` for seq + idempotent send.
