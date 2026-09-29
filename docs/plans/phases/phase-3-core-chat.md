# Phase 3 — Core chat features

**Weeks:** 3–4 · **Depends on:** P1 · **ADRs:** 0012 · **Design:** [03-chat-system-design.md](../architecture/03-chat-system-design.md) §2–4, §9, §12

## Goal
Complete the Messenger-level feature set on the REST side, with the data model that realtime (P4) and events (P6) need:
- Ordering (per-channel seq).
- Idempotent send.
- Edit/delete.
- Replies and reactions.
- Read state and unread counts.
- Role-based channel management.

## Why it matters (interview angle)
These are the "design a chat app" data-model questions: ordering, idempotency, unread counts at O(1), soft delete, and authorization.

## Scope
- **In:**
  - Schema changes.
  - Use cases (edit, delete, react, read, reply, leave, remove member, search).
  - REST endpoints.
  - The WS `SEND` path updated to use seq and idempotency (the full protocol v2 is P4).
- **Out:**
  - Presence and typing (P4).
  - Notifications (P6).
  - Semantic search (P8).

## Design notes
- **Send transaction** (see ADR-0012). The retry path must not bump `last_seq`:
  1. `SELECT id, seq FROM message WHERE sender_id=? AND client_msg_id=?` → if found, return it (idempotent hit).
  2. Otherwise bump `channel.last_seq` and insert. The unique-violation race (two concurrent retries) is caught and re-read.
- **Edit:** only the sender, within 24 h, only text/markdown. `version = version + 1`, `edited_time = now()`. Optimistic lock with `@Version` or `WHERE version = :expected`.
- **Delete:** soft delete (`deleted_time`), by the sender or a channel CREATOR/LEADER. Content is nulled in API responses and shown as "message deleted". Media objects are removed from S3 asynchronously in P6.
- **Reactions:** `message_reaction(message_id, user_id, emoji, created_time)` with PK `(message_id, user_id, emoji)`. Toggle semantics.
- **Read state:** `PUT /channels/{id}/read {seq}` → `last_read_seq = GREATEST(...)`.
- **Channel roles:** use the role table from P1-T06, then add remove member, leave, and transfer leader (Forum).
- **Media upload:** move to browser-direct `POST /medias/presign-upload` → S3 presigned PUT (key and content-type bound, size limit via policy), then `SEND` with the key. The existing multipart endpoint stays until the frontend switches.

## Tasks
- [ ] **P3-T01** Migration:
  - Ids `INTEGER` → `BIGINT` (all PK/FK).
  - Update entities and domain types (`Integer` → `Long`) consistently across ports, DTOs and mappers.
- [ ] **P3-T02** Migration: `channel.last_seq`, `channel.last_message_at`, `message.seq` (backfill + `UNIQUE(channel_id, seq)`), `message.client_msg_id UUID` + `UNIQUE(sender_id, client_msg_id)`, `message.version`, `member.last_read_seq`; index `(channel_id, seq DESC)`
- [ ] **P3-T03** `MessageService.save`: seq assignment + idempotent send. Unit tests: first send, retry returns the same seq without a bump, concurrent retry race (IT)
- [ ] **P3-T04** Outbound WS event includes `messageId`, `seq`, `createdTime` (the minimum needed before P4)
- [ ] **P3-T05** History API: `GET /channels/{id}/messages?beforeSeq=&limit=` and `?afterSeq=&limit=`. Keep `cursorId` as a deprecated alias for one phase
- [ ] **P3-T06** Edit message: `EditMessageUseCase`, `PATCH /messages/{id}`, sender-only, 24 h window, version check → 409 on conflict
- [ ] **P3-T07** Delete message: `DeleteMessageUseCase`, `DELETE /messages/{id}`, sender or channel admin, soft delete
- [ ] **P3-T08** Replies: `reply_to_id` (same channel enforced) + a reply preview in `MessageItem`
- [ ] **P3-T09** Reactions: table, `PUT/DELETE /messages/{id}/reactions/{emoji}`, aggregated counts in history responses (one grouped query per page, no N+1)
- [ ] **P3-T10** Read state: `PUT /channels/{id}/read`; the channel list returns `unreadCount` and `lastMessage` preview
- [ ] **P3-T11** Member management: remove member (admin), leave channel (self), transfer leader (Forum). Membership checks everywhere
- [ ] **P3-T12** DM convenience: `POST /channels/direct {userId}` returns the existing PRIVATE_CHAT or creates it (unique pair constraint)
- [ ] **P3-T13** Keyword search: `tsvector` generated column + GIN index; `GET /channels/{id}/messages/search?q=` (membership-checked). Add a global search across my channels
- [ ] **P3-T14** Presigned PUT upload endpoint + validation. Mark the multipart upload as deprecated
- [ ] **P3-T15** A MARKDOWN message strategy (currently missing → `InvalidMessageConfigException`)
- [ ] **P3-T16** `UNSUBSCRIBE` wired end to end (currently falls through to "Unsupported action"); the `WsUnsubscribeService` stub implemented

## Files touched
- `domain/chat/**`, `domain/channel/**`, `domain/member/**`
- `application/service/chat/**`, `application/service/channel/**`
- `adapters/out/persistence/**`
- `adapters/in/web/controller/{Message,Channel,Media}Controller.java` + DTOs/mappers
- `db/migration/V*`

## Test plan
- **Unit (Mockito):** each new service. Idempotency and the edit window/ownership rules.
- **IT (Testcontainers):**
  - Seq assignment under concurrency: 20 parallel sends to one channel → seqs 1..20 with no gaps.
  - Idempotent retry race.
  - Unread count after reads.
  - FTS query.
  - Authz matrix for edit/delete/remove.
- **Mapper tests** for the new DTOs (existing pattern: `*DtoMapperTest`).

## Definition of Done
All endpoints are documented in Swagger, and the tests pass. The WS push carries `messageId`, `seq` and `createdTime`, and resending the same `clientMsgId` never creates a duplicate.

## Interview talking points
- Per-channel seq with a row lock: why it's correct, what it costs, and when it breaks.
- Idempotency keys: where the key lives, what the second response contains, and the race.
- O(1) unread counts vs counting queries.
- Soft delete and what "deleted" means for search, RAG and legal requests.

## Risks
- The `Integer` → `Long` change ripples through every layer. Do it first, alone, in one PR.
- The seq backfill on existing rows must run before adding the NOT NULL + unique constraint (expand/contract).
