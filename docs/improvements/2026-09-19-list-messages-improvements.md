# Improvements — List Messages in a Channel

**Date:** 2026-09-19
**Branch:** `feat/list-messages-in-a-chat-room`
**Endpoint:** `GET /api/v1/channels/{channelId}/messages?cursorId=&limit=`

Already fixed on this branch: `limit` validation (`@Min(1) @Max(100)`, default 20, 422 via `HandlerMethodValidationException` handler), empty-channel crash, NPE on non-media messages without media, soft-deleted messages excluded from the list, channel existence + membership check on read (404 `CHANNEL_401` / 403 `MEMBER_404`), web response uses `MessageItemResponse` instead of domain `MessageItem`, entity→`MessageItem` mapping moved to MapStruct `MessageMapper.toItem` and pagination (`hasMore`/cursor) moved to `MessageService`, `USER_ID` column rename (local DB recreated), presign removed from the message list — `content` holds the S3 key for media and clients call `GET /api/v1/medias/presigned-url` (resolves the in-place mutation of `MessageItem`).

> ⚠️ **Release blocker:** the presigned-url endpoint is now the only gate to media and has no membership check. See `docs/improvements/2026-09-19-media-authorization.md` #1.

The items below are still open.

---

## High

### 1. `ecs` schema needs the `SENDER_USER_ID` → `USER_ID` change
**File:** `adapters/out/persistence/message/entity/MessageEntity.java`

`ecs` runs `ddl-auto=validate` with Flyway disabled, so the app will fail on startup against the existing RDS schema. Apply before deploying:

```sql
BEGIN;
ALTER TABLE message ADD COLUMN user_id INTEGER;
UPDATE message SET user_id = sender_user_id;
ALTER TABLE message ALTER COLUMN user_id SET NOT NULL;
ALTER TABLE message ADD CONSTRAINT fk_message_user FOREIGN KEY (user_id) REFERENCES <user_table>(id);
ALTER TABLE message DROP COLUMN sender_user_id;
COMMIT;
```

Longer term: enable Flyway and keep migrations in `src/main/resources/db/migration` so schema changes are versioned.

---

## Medium

### 2. `VIDEO` messages cannot be created
**File:** `application/service/chat/strategy/MediaMessageCreationStrategy.java`

`VIDEO` is an allowed upload type (mp4), but only `IMAGE` has a creation strategy, so saving a video message throws `InvalidMessageConfigException`.

**Fix:** add `ContentType.isMedia()` and let `MediaMessageCreationStrategy` handle both `IMAGE` and `VIDEO` (or register one strategy per type).

### 3. Full `User` loaded (with password hash) just to set a FK
**Files:** `application/service/chat/strategy/AbstractMessageCreationStrategy.java`, `domain/chat/model/Message.java`

`buildMessage` calls `LoadUserPort.loadById` on every message save — an extra query — and the domain `Message` now carries a full `User` including the password hash. If a `Message` is ever serialized (e.g. broadcast over WebSocket) the hash leaks.

**Fix:** keep `Integer senderUserId` on `Message`; in `MessageAdapter.save` resolve it with `userRepository.getReferenceById(id)` (no SELECT).

### 4. Validation order changed in creation strategies
**Files:** `TextMessageCreationStrategy.java`, `MediaMessageCreationStrategy.java`

`getChannel` moved into `buildMessage`, so membership is now checked before channel existence. A non-existent channel now returns `MemberNotFoundException` instead of `ChannelNotFoundException`.

**Fix:** look up the channel first in `createMessage` (or in a shared template method in the abstract class) and pass it into `buildMessage`.

### 5. Transaction boundaries
**Files:** `MessageAdapter.java`, `MessageService.java`

- `MessageAdapter.save` is `@Transactional` although `MessageService.save` already is — transaction boundaries belong in the application service.
- `MessageService.getMessages` has no transaction; mark it `@Transactional(readOnly = true)`.

### 6. Missing index for cursor pagination
The query filters on `channel_id` and orders by `id DESC`. Add a composite index `(channel_id, id DESC)` (a partial index `WHERE deleted_time IS NULL` matches the query best) once migrations exist.

---

## Low

### 7. Inbound `*DtoMapper`s are hand-written
All inbound web mappers (`AuthDtoMapper`, `ChannelDtoMapper`, `MediaDtoMapper`, `MessageDtoMapper`) are `@Component` classes while persistence mappers use MapStruct. Consider migrating them together later for consistency — not piecemeal.

### 8. Unused `MediaPort` / `MediaAdapter`
Media is now saved via cascade from `MessageEntity`. Delete `domain/media/port/out/MediaPort.java` and `adapters/out/persistence/media/MediaAdapter.java` unless something else needs them.

### 9. Wrong log / exception text in `MessageService.save`
`"No message creation strategy registered for type null"` and `"Unsupported channel type: "` — should read e.g. `"No message creation strategy for content type: " + command.contentType()`.

### 10. `buildMessage` visibility widened
`MediaMessageCreationStrategy.buildMessage` overrides a `protected` method as `public`. Keep it `protected`.

### 11. Missing tests
Add Mockito unit tests (`@ExtendWith(MockitoExtension.class)`):
- `MessageService.getMessages` — rejects missing channel and non-members; returns media `content` as the S3 key unchanged.
- `MessageService.getMessages` pagination — empty list, exactly `limit` rows (`hasMore=false`), `limit + 1` rows (`hasMore=true`, cursor = last returned id).
- `MessageMapper.toItem` — media path vs text content, user fields.
- `Text/MediaMessageCreationStrategy` — updated `buildMessage` and media attachment.
- `MessageController` — `limit=0` / `limit=101` → 422.
