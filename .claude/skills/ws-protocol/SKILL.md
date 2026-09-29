---
name: ws-protocol
description: DevCool WebSocket protocol v2 reference (frame types, payloads, ACK/NACK, close codes, resume). Load when reading or changing WebSocket handlers, realtime adapters or the frontend ws client.
user-invocable: false
paths: "src/main/java/**/websocket/**,src/main/java/**/realtime/**,frontend/src/ws/**"
---

# WebSocket protocol v2

The source of truth is ADR-0005 (`docs/plans/architecture/adr/0005-raw-websocket-protocol-v2.md`) and `docs/plans/architecture/03-chat-system-design.md`. If this reference and those docs disagree, the docs win; fix this file.

## Connect
1. `POST /api/v1/ws/ticket` (Bearer) → `{ "ticket": "…", "expiresIn": 30 }`.
2. `wss://<origin>/ws?ticket=<ticket>`. The ticket is single-use (`GETDEL`). Invalid or missing → handshake 401.
3. The origin must be in the allowlist.

## Envelope
```json
{ "v": 2, "type": "TYPE", "reqId": "string, client-chosen", "channelId": 12, "payload": {} }
```
Each client frame with a `reqId` gets exactly one `ACK` or `NACK` with the same `reqId`.

## Client → server
| type | payload | server reply |
|---|---|---|
| `SUBSCRIBE` | — (uses `channelId`) | `ACK`; membership required |
| `UNSUBSCRIBE` | — | `ACK` |
| `SEND` | `{clientMsgId: uuid, contentType: TEXT\|MARKDOWN\|IMAGE\|VIDEO, content?, mediaKey?, replyToId?}` | `ACK {clientMsgId, messageId, seq, createdTime}`; the same result is returned for a retried `clientMsgId` |
| `EDIT` | `{messageId, content, expectedVersion}` | `ACK {version}` or `NACK {code: CONFLICT\|FORBIDDEN\|EDIT_WINDOW_EXPIRED}` |
| `DELETE` | `{messageId}` | `ACK` |
| `REACT` | `{messageId, emoji, on: bool}` | `ACK` |
| `TYPING` | — | none (fire-and-forget, rate-limited) |
| `READ` | `{seq}` | none |
| `RESUME` | `{lastSeq}` | `MESSAGE_NEW` × k in seq order, then `ACK {upToSeq}`; or `RESYNC_REQUIRED` |
| `PING` | — | `PONG` (refreshes presence) |
| `ASK` | `{question}` | `AI_CHUNK`… then `AI_DONE` or `AI_ERROR`, all with the same `reqId` |
| `ASK_CANCEL` | `{askReqId}` | `ACK` |

## Server → client
| type | payload |
|---|---|
| `ACK` / `NACK` | `{…result}` / `{code, message, retryAfterMs?}`. Codes: `UNSUPPORTED`, `INVALID`, `FORBIDDEN`, `NOT_FOUND`, `CONFLICT`, `RATE_LIMITED`, `INTERNAL` |
| `MESSAGE_NEW` | `{messageId, seq, senderId, contentType, content, mediaKey?, replyTo?, createdTime}` |
| `MESSAGE_UPDATED` | `{messageId, seq, content, version, editedTime}` |
| `MESSAGE_DELETED` | `{messageId, seq}` |
| `REACTION` | `{messageId, emoji, userId, on}` |
| `TYPING` | `{userId}` (client expires it after 5 s) |
| `PRESENCE` | `{userId, status: online\|offline, lastSeen?}` |
| `READ_RECEIPT` | `{userId, seq}` |
| `AI_CHUNK` / `AI_DONE` / `AI_ERROR` | `{text}` / `{citations: [messageId], usage, fallback?}` / `{code, message}` |
| `RESYNC_REQUIRED` | `{latestSeq}` → the client refetches over REST |
| `RECONNECT` | `{afterMs}` → the server is draining; reconnect after the delay |
| `ERROR` | `{code, message}` for frames that couldn't be parsed at all |
| `PONG` | — |

## Close codes
`1012` restart (after `RECONNECT`) · `1013` overloaded / slow consumer · `4400` protocol version not supported · `4401` unauthenticated.

## Invariants
- Commit → ACK → publish, always.
- Message events always carry `messageId` and `seq`. Clients dedupe by `messageId` and detect gaps by `seq`.
- A frame error never closes the socket.
- The sender's connection is skipped via `originConnectionId`.
