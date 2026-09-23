# Improvements — List Messages in a Channel

**Date:** 2026-09-19
**Branch:** `feat/list-messages-in-a-chat-room`
**Endpoint:** `GET /api/v1/channels/{channelId}/messages?cursorId=&limit=`

**Fixed on this branch:**

- **`limit` validation** — `@Min(1) @Max(100)`, default 20; 422 via the `HandlerMethodValidationException` handler
- **Empty-channel crash** on the first page
- **NPE** on non-media messages with no media row
- **Soft-deleted messages** excluded from the list
- **Authorization on read** — channel exists (404 `CHANNEL_401`) → caller is a member (403 `MEMBER_404`)
- **Web response** returns `MessageItemResponse`, not the domain `MessageItem`
- **Mapping** moved to MapStruct `MessageMapper.toItem`; **pagination** (`hasMore`/cursor) moved to `MessageService`
- **`USER_ID` column rename** (local DB recreated)
- **Presigning removed from the list** — `content` holds the S3 key and clients call `GET /api/v1/medias/presigned-url`; also removes the in-place mutation of `MessageItem`
- **Transaction boundary** — dropped from `MessageAdapter.save`, so `MessageService.save` is the only one
- **`Message` carries `senderId` / `channelId`** instead of full `User` / `Channel` — the password hash can no longer reach a message, and creation dropped from `7 + N` queries to a flat 3
- **Log / exception text** in `MessageService.save` — SLF4J placeholder with the real content type; exception reads "Unsupported content type"
- **`VIDEO` messages** — `VideoMessageCreationStrategy` added; it shares the media `buildMessage` with `ImageMessageCreationStrategy` via `AbstractMediaMessageCreationStrategy`
- **Dead media persistence removed** — `MediaPort`, `MediaAdapter` and `MediaRepository` deleted; media is saved via cascade from `MessageEntity`

> Two behaviour notes on the `senderId` change: both FKs resolve via `em.getReference(...)` (matching `MemberAdapter.addMembers`) rather than `userRepository.getReferenceById(...)` as originally proposed; and a deleted user with a live JWT now gets `MemberNotFoundException` (403) instead of `UserNotFoundException` (404).

> ⚠️ **Release blocker:** the presigned-url endpoint is now the only gate to media and has no membership check. See `docs/improvements/2026-09-19-media-authorization.md` #1.

The items below are still open.

---

## High

### 1. `ecs` cannot start against a fresh RDS database
**File:** `src/main/resources/application-ecs.properties`

The RDS database was dropped to save cost, so the `SENDER_USER_ID` → `USER_ID` migration is no longer needed. Nothing is left to migrate.

When a new RDS instance is created, though, nothing creates the schema: `ecs` runs `ddl-auto=validate` with `spring.flyway.enabled=false`, and there is no `src/main/resources/db/migration` folder. Hibernate validation fails on the missing tables and the app will not start.

**Fix (recommended):** add a Flyway baseline `db/migration/V1__init.sql` with the current schema (generate it from the `local` profile's Hibernate DDL), then set `spring.flyway.enabled=true` in `application-ecs.properties`. `flyway-core` is already in `pom.xml`. Future schema changes (such as the #3 index) then ship as versioned migrations.

**Quick alternative:** set `SPRING_JPA_HIBERNATE_DDL_AUTO=update` for the first ECS deploy only, then remove it so `validate` applies again. Later schema changes will hit the same problem.

---

## Medium

### 3. Missing index for cursor pagination
The query filters on `channel_id` and orders by `id DESC`. Add a composite index `(channel_id, id DESC)` (a partial index `WHERE deleted_time IS NULL` matches the query best) once migrations exist.

---

## Low

### 4. Inbound `*DtoMapper`s are hand-written
All inbound web mappers (`AuthDtoMapper`, `ChannelDtoMapper`, `MediaDtoMapper`, `MessageDtoMapper`) are `@Component` classes while persistence mappers use MapStruct. Consider migrating them together later for consistency — not piecemeal.

### 6. Missing tests
Add Mockito unit tests (`@ExtendWith(MockitoExtension.class)`):
- `MessageService.getMessages` — rejects missing channel and non-members; returns media `content` as the S3 key unchanged.
- `MessageService.getMessages` pagination — empty list, exactly `limit` rows (`hasMore=false`), `limit + 1` rows (`hasMore=true`, cursor = last returned id).
- `MessageMapper.toItem` — media path vs text content, user fields.
- `AbstractMessageCreationStrategy.createMessage` (exercised through either subclass) — missing channel → `ChannelNotFoundException` before any membership lookup; non-member → `MemberNotFoundException`; happy path → `messagePort.save` called once.
- `AbstractMediaMessageCreationStrategy.buildMessage` (via `Image`/`VideoMessageCreationStrategy`) — media attached with `content` moved to `Media.path` and `Message.content` nulled.
- `MessageController` — `limit=0` / `limit=101` → 422.
