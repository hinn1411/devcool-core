# Lessons Learned

Mistakes found while reviewing `feat/list-messages-in-a-chat-room` (commit `e6d90e5`), written down so they don't come back in the next feature.

Each lesson: **What happened** (real example from this codebase) → **Why it matters** → **Rule** to remember.

---

## A. Input & edge cases

### 1. Put validation on the right parameter — and make sure it returns 4xx
- **What happened:** `@Min(1) @Max(100)` sat on `cursorId` instead of `limit` in `MessageController`. `limit` was unbounded; `limit=0` broke `PageRequest.of`. On top of that, a failed constraint threw `HandlerMethodValidationException`, which fell into the catch-all handler → **500**.
- **Why it matters:** clients can request huge pages (DB/memory pressure), and bad input looks like a server crash.
- **Rule:** after adding a constraint, call the endpoint with a bad value and check you get **4xx**. In Spring 6.1+, parameter constraints run without `@Validated` and throw `HandlerMethodValidationException` — make sure `ApiExceptionHandler` handles it.

### 2. Always handle the empty case
- **What happened:** `messages.getLast()` in `MessageAdapter` threw `NoSuchElementException` on a channel with no messages.
- **Why it matters:** the very first screen of a new channel returned 500.
- **Rule:** for every list you index into (`get(0)`, `getLast()`, `subList`), ask "what if it's empty?" — return early:
  ```java
  if (fetched.isEmpty()) {
    return new MessageList(List.of(), null, false);
  }
  ```

### 3. Don't assume an optional relation exists
- **What happened:** `contentType == TEXT ? content : media.getPath()` — a `MARKDOWN` message (no media) → NPE.
- **Why it matters:** adding a new enum value silently breaks old code.
- **Rule:** branch on the **data** (`media != null`), not on an enum that "implies" it.

### 4. Use wrapper types for values that can be absent
- **What happened:** `GetMessagesResponse.cursorId` was a primitive `int` — it can't represent "no cursor".
- **Rule:** nullable in the domain → `Integer`/`Long` in the DTO. Primitives only for values that always exist (e.g. `boolean hasMore`).

### 5. Remove debug defaults before committing
- **What happened:** `@RequestParam(defaultValue = "2") Integer limit` — a value left over from local testing.
- **Rule:** grep your diff for suspicious small numbers, `TODO`, `System.out`, and local-only config before pushing.

---

## B. Security

### 6. Authenticated ≠ authorized
- **What happened:** any logged-in user could read any channel's messages by changing `{channelId}`.
- **Why it matters:** private chats leak to anyone with an account.
- **Rule:** every endpoint that reads/writes channel data checks membership **in the service layer**. Reuse the existing check:
  ```java
  memberPort.findMemberOfChannelByUserId(channelId, userId)
  ```

### 7. Check existence before permission
- **What happened:** after a refactor, creation strategies checked membership before the channel existed → a missing channel returned "member not found".
- **Rule:** order checks: **exists (404) → allowed (403) → do work.** Error messages should describe the real problem.

### 8. Every domain exception needs an HTTP status
- **What happened:** `HttpErrorMapper` only mapped 3 codes; `CHANNEL_NOT_FOUND` and `MEMBER_NOT_FOUND` fell to `default -> 500`.
- **Rule:** when you throw a domain exception in a new flow, check its `ErrorCode` is mapped in `HttpErrorMapper`.

### 9. Anything that hands out access must authorize
- **What happened:** `GET /medias/presigned-url` signs **any** key; `POST /medias/upload` accepts any `channelId`. (See `2026-09-19-media-authorization.md`.)
- **Why it matters:** a presigned URL *is* access. Knowing an S3 key (e.g. after leaving a channel) should not be enough.
- **Rule:** treat "generate a URL/token/key" endpoints like data endpoints — same membership check.

---

## C. Data & persistence

### 10. Soft delete means filtering in every read
- **What happened:** the message list query didn't filter `deletedTime`, so deleted messages came back.
- **Rule:** if an entity has `deleted_time`, every read query needs `AND x.deletedTime IS NULL` (or a Hibernate `@SQLRestriction` on the entity so you can't forget).

### 11. A schema change needs a migration plan
- **What happened:** `SENDER_USER_ID` → `USER_ID` was renamed only in the entity. Flyway is disabled; `ecs` runs `ddl-auto=validate` → startup failure. Locally, `update` added `USER_ID` but **never drops** the old `NOT NULL` column → inserts fail.
- **Rule:**
  - `ddl-auto=update` adds, never removes/renames.
  - Every column rename/drop needs a SQL script (backfill → constraint → drop) for every environment.
  - Double-check `application-*.properties` in your diff — don't commit local tweaks by accident.

### 12. Cursor pagination pattern
- **What happened:** the cursor math was buggy and lived in the adapter.
- **Rule:**
  ```java
  List<T> fetched = port.find(channelId, cursorId, limit + 1); // one extra row
  boolean hasMore = fetched.size() > limit;
  List<T> items   = hasMore ? fetched.subList(0, limit) : fetched;
  Integer cursor  = items.getLast().getId();                   // last RETURNED row
  ```
  Query with `WHERE id < :cursorId ORDER BY id DESC`, backed by an index on `(channel_id, id)`.

---

## D. Architecture (hexagonal)

### 13. Don't return domain models from web DTOs
- **What happened:** `GetMessagesResponse.items` was `List<MessageItem>` (domain). `GetProfileResponse` still exposes domain enums `Role`/`UserStatus`.
- **Why it matters:** renaming a domain field silently changes the public API.
- **Rule:** web DTOs import nothing from `com.devcool.domain`. Map to a response record (`MessageItemResponse`, `ChannelListItemResponse`); convert enums with `.name()`. Quick check:
  ```bash
  grep -rn "import com.devcool.domain" src/main/java/com/devcool/adapters/in/web/dto/response/
  ```

### 14. Keep adapters dumb
- **What happened:** `MessageAdapter` built `MessageItem` by hand, chose "media path vs text", and computed `hasMore`/cursor.
- **Rule:** adapter = fetch + map. Mapping → MapStruct mapper (`MessageMapper.toItem`). Decisions (pagination, rules) → application service.

### 15. One field, one meaning — don't mutate to change it
- **What happened:** `MessageService` overwrote `MessageItem.content` (S3 key) with a presigned URL.
- **Why it matters:** the same field meant different things depending on *when* you read it; any cache/reuse would store an expiring URL.
- **Rule:** if a value changes meaning, give it a new field or a new object. Prefer immutable `record`s for read models.

### 16. Follow the existing convention before introducing a new one
- **What happened:** I suggested turning `MessageDtoMapper` into MapStruct — but every inbound mapper here is a `@Component`. The right fix was copying `ChannelDtoMapper`.
- **Rule:** before writing new code, find the nearest existing example of the same thing and match it. Change conventions project-wide, deliberately — not one file at a time.

### 17. Transactions belong in the service
- **What happened:** `@Transactional` on both `MessageService.save` and `MessageAdapter.save`; none on the read path.
- **Rule:** `@Transactional` on application service methods; `@Transactional(readOnly = true)` for queries; not on adapters.

---

## E. Hygiene

### 18. Watch for copy-paste leftovers
- **What happened:** `CreateChannelResponse` has `@Schema(name = "GetProfileResponse")` (collides in Swagger); `AuthDtoMapper.toProfileResponse` never sets `id`.
- **Rule:** after copying a class, re-read every annotation and every builder call against the new class's fields.

### 19. Delete what a refactor made unused
- **What happened:** after media moved to cascade save, `MediaPort`/`MediaAdapter` became dead code; `MessageService` kept an unused `MediaStoragePort`.
- **Rule:** after a refactor, search for usages of what you replaced and remove it in the same PR.

### 20. Run the CI checks locally before pushing
- **What happened:** wildcard imports (`org.springframework.web.bind.annotation.*`, `lombok.*`), double spaces, long lines.
- **Rule:**
  ```bash
  ./mvnw spotless:apply
  ./mvnw -B -q -DskipTests -DskipITs -Pstatic-analysis verify
  ./mvnw verify
  ```

### 21. Write the edge-case tests together with the feature
- **Rule:** for a paginated list, the minimum set is: empty result, exactly `limit` rows (`hasMore=false`), `limit + 1` rows (`hasMore=true`), non-member (403), missing channel (404), invalid `limit` (422).

---

## Pre-PR checklist

Copy into the PR description:

```markdown
- [ ] Validation is on the right params; bad input returns 4xx (tested manually)
- [ ] Empty lists and null relations handled
- [ ] Nullable DTO fields use wrapper types; no leftover debug defaults
- [ ] Endpoint checks exists (404) → member (403) in the service
- [ ] New domain exceptions are mapped in HttpErrorMapper
- [ ] Endpoints that issue URLs/tokens also check authorization
- [ ] Read queries filter soft-deleted rows
- [ ] Schema changes have a SQL script for every environment; no local config committed
- [ ] Web DTOs import nothing from com.devcool.domain
- [ ] Adapters only fetch + map; business rules live in the service
- [ ] Matches the nearest existing example (mapper style, naming, structure)
- [ ] Dead code from the refactor removed
- [ ] spotless + static-analysis + verify pass locally
- [ ] Unit tests for edge cases (empty, limit, limit+1, 403, 404, 422)
```
