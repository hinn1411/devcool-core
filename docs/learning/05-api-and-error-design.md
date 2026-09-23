# 05 — API & Error Design

Your errors are an API. Clients branch on them, proxies retry on them, on-call dashboards page on them. They deserve the same design attention as your success responses — and right now they are the least designed part of the system.

---

## 1. The status line and the body must agree

**The concept.** The HTTP status line is the machine-readable contract. Clients branch on it, CDNs cache on it, retry libraries back off on it, and monitoring alerts on it. A `status` field inside the body is metadata for humans reading logs. When they disagree, everything automated follows the status line.

**In your code** — `adapters/in/web/handler/ApiExceptionHandler.java:91-100`:

```java
@ExceptionHandler(AuthorizationDeniedException.class)
public ResponseEntity<ApiErrorResponse> handleForbiddenRequest(Exception ex) {
  return ResponseEntity.internalServerError()      // ← 500
      .body(
          ApiResponseFactory.error(
              HttpStatus.FORBIDDEN,                 // ← body says 403
              ErrorCode.FORBIDDEN.code(),
              ex.getMessage(),
              Map.of("error", ex.getClass().getSimpleName())));
}
```

**Why it matters.** Every authorization denial — a routine, expected, client-caused event — arrives as a server error. It pages on-call, it pollutes your error budget, and a client with retry-on-5xx logic will hammer the endpoint it is not allowed to call. It also leaks `ex.getMessage()`, which for Spring Security includes the denied method signature.

**The fix.** `ResponseEntity.status(HttpStatus.FORBIDDEN)`, and drop the message.

**Where else this applies.** Any handler that builds the status twice. The deeper rule: **one fact, one source of truth.** You'll see the same shape in `totalOfMembers` vs `members.size()` (chapter 01) and in the three different refresh-token lifetimes (§7).

---

## 2. `default ->` over your own enum throws away the compiler

**The concept.** Java 21 pattern switches over an enum are exhaustive *without* a `default` branch — the compiler errors if you miss a constant. Adding `default` converts "you forgot a case" from a **compile error** into a **silent runtime fallback**.

**In your code** — `adapters/in/web/util/HttpErrorMapper.java:9-18`:

```java
public static HttpStatus toHttpStatus(ErrorCode code) {
  return switch (code) {
    case USER_NOT_FOUND -> HttpStatus.NOT_FOUND;
    case EMAIL_ALREADY_USED -> HttpStatus.CONFLICT;
    case PASSWORD_WEAK -> HttpStatus.UNPROCESSABLE_ENTITY;
    case CHANNEL_NOT_FOUND -> HttpStatus.NOT_FOUND;
    case MEMBER_NOT_FOUND -> HttpStatus.FORBIDDEN;
    default -> HttpStatus.INTERNAL_SERVER_ERROR;
  };
}
```

Five of ~23 `ErrorCode` constants are mapped. Everything else returns **500**, including:

`PASSWORD_INCORRECT` · `REFRESH_TOKEN_INVALID` · `USERNAME_ALREADY_USED` · `USER_DUPLICATE` · `INVALID_CHANNEL_CONFIG` · `DUPLICATE_MEMBER` · `UNSUPPORTED_MEDIA_TYPE` · `TOO_LARGE_MEDIA` · `INVALID_MEDIA_CONTENT` · `INVALID_MESSAGE_CONFIG` · `FORBIDDEN`

**Why it matters.** A wrong password returns 500 — carrying the plaintext password (chapter 02 §2). Uploading a PDF returns 500. A duplicate username returns 500 while a duplicate email returns 409, for no reason a client can discover. Clients cannot distinguish "your input was bad, don't retry" from "we broke, retry with backoff". Every one of these inflates your 5xx rate.

**The fix.** Delete `default`. The compiler then names every unmapped constant. Better still, put the status on `ErrorCode` itself so adding a code *forces* the decision:

```java
USER_NOT_FOUND("USR_404", HttpStatus.NOT_FOUND),
PASSWORD_INCORRECT("USR_223", HttpStatus.UNAUTHORIZED),
```

**Verify it yourself.** One parameterised test catches all of them:

```java
@ParameterizedTest
@EnumSource(ErrorCode.class)
void everyErrorCodeHasADeliberateStatus(ErrorCode code) {
  assertThat(HttpErrorMapper.toHttpStatus(code))
      .isNotEqualTo(HttpStatus.INTERNAL_SERVER_ERROR);
}
```

**Where else this applies.** Any `switch` over an enum you own. `lessons.md` #8 says "check your ErrorCode is mapped" — that's the manual version of this rule. Make the compiler do it instead.

---

## 3. The catch-all handler is the one place that must log

**In your code** — `ApiExceptionHandler.java:80-89`:

```java
@ExceptionHandler(Exception.class)
public ResponseEntity<ApiErrorResponse> handleGeneric(Exception ex) {
  return ResponseEntity.internalServerError()
      .body(
          ApiResponseFactory.error(
              HttpStatus.INTERNAL_SERVER_ERROR,
              ErrorCode.INTERNAL_SERVER_ERROR.code(),
              "Unexpected server error",
              Map.of("error", ex.getClass().getSimpleName())));
}
```

The class has no logger. Nothing is logged.

**Why it matters.** `@RestControllerAdvice` *suppresses* Spring's own `DispatcherServlet` error logging. So every unexpected exception in the application — every NPE catalogued in these notes — vanishes with **no stack trace anywhere**. You get a 500 in production and have literally nothing to debug with.

It's also backwards on information flow: the client is told `NullPointerException` (a usable probing oracle — it distinguishes a crash from a real failure) while the operator is told nothing.

**The fix.**

```java
@ExceptionHandler(Exception.class)
public ResponseEntity<ApiErrorResponse> handleGeneric(Exception ex) {
  String traceId = UUID.randomUUID().toString();
  log.error("Unexpected error [{}]", traceId, ex);
  return ResponseEntity.internalServerError()
      .body(ApiResponseFactory.error(
          HttpStatus.INTERNAL_SERVER_ERROR, ErrorCode.INTERNAL_SERVER_ERROR.code(),
          "Unexpected server error", Map.of("traceId", traceId)));
}
```

The client gets a correlation id to quote; you get the stack trace. Credit where due: not leaking the stack trace to the client was the right instinct — it just needs the other half.

---

## 4. Returning `null` from a controller sends HTTP 200

**In your code** — `adapters/in/web/controller/AuthController.java:140-144`:

```java
@PostMapping("/password")
public ResponseEntity<ApiSuccessResponse<Boolean>> changePassword(
    @Valid @RequestBody ChangePasswordRequest request) {
  return null;
}
```

Spring interprets a `null` return as "the handler already wrote the response" and commits an empty **200 OK**.

**Why it matters.** The most dangerous possible default for an unfinished endpoint: it reports success. See chapter 02 §5 for the security consequences.

**The rule.** An unfinished endpoint throws. `throw new UnsupportedOperationException()` → 500 is ugly but honest; `501 Not Implemented` is better. Never `return null`.

---

## 5. An exception hierarchy only works if it is used exhaustively

**In your code** — `application/service/RefreshTokenService.java:38-40`:

```java
if (!refreshStore.consumeIfValid(jtiHash)) {
  throw new CredentialsExpiredException("Refresh token invalid, expired or already in used");
}
```

imported from `org.springframework.security.authentication`. Thirty lines later, the same class throws `RefreshTokenInvalidException` — the domain exception that already exists for exactly this case, with `ErrorCode.REFRESH_TOKEN_INVALID` already defined.

**Why it matters.** `CredentialsExpiredException` is not a `DomainException`, so it skips `handleDomainException` entirely and lands in the catch-all → **500 instead of 401**. The client cannot tell "log in again" from "the server is down". It also drags Spring Security into the application layer (chapter 03).

Same pattern at `MediaService.java:54`:

```java
} catch (IOException e) {
  throw new RuntimeException("Failed to read upload stream", e);
}
```

`RuntimeException` is uncatchable by type and unmappable — the "I don't want to think about this" exception.

**The rule.** One escape from the hierarchy disables the mapping layer you built for everything else. If you define a taxonomy, every throw site in the core must use it.

---

## 6. Error message text is the primary API for a 4xx

A client receiving a 422 cannot read your source. The message *is* the contract.

**In your code** — `application/service/channel/strategy/ForumCreationStrategy.java:65-68`:

```java
if (Objects.isNull(expiredTime)) {
  log.info("Forum must not have expired time");
  throw new InvalidChannelConfigException("Forum must not have expired time");
}
```

The condition fires when `expiredTime` is **absent**. The message says it must not be **present**. Compare the correct inverse at `PrivateChatCreationStrategy.java:69-72` (`Objects.nonNull(expiredTime)` → same message) — the text was copy-pasted and the condition inverted.

**Why it matters.** A client creating a permanent forum omits `expiredTime`, is told "Forum must not have expired time", removes something that isn't there, and is stuck. The error is unactionable and the API is unusable without reading the source.

**Where else this applies.** This is one pattern with three instances. Two are now fixed, and the before/after is the clearest illustration of the rule:

**`MessageService.save` — fixed 2026-09-21.**

```java
// before — "null" hardcoded into the text, and "channel" where it meant "content"
log.warn("No message creation strategy registered for type null");
throw new InvalidMessageConfigException("Unsupported channel type: " + command.contentType());

// after
log.warn(
    "No message creation strategy registered for contentType: {}", command.contentType());
throw new InvalidMessageConfigException("Unsupported content type: " + command.contentType());
```

Note which half was fixed first. The log line — internal, read by you — was corrected a commit before the exception message, which is the one that reaches the client through `DomainException.getMessage()` → `ApiExceptionHandler:27`. That instinct is backwards, and worth catching in yourself: **the string in the exception is API, the string in the log is a note to yourself.**

**`ChannelService.java:54` — still open, and it is the original.**

```java
log.warn("No channel creation strategy registered for type null");
throw new InvalidChannelConfigException("Unsupported channel type: " + type);
```

`MessageService.save` is a copy of this method — same `Objects.isNull(strategy)` guard, same message shape. Here `"channel type"` is genuinely correct, but `"type null"` is the identical hardcoded-null defect, with no placeholder and no interpolated value.

**This is the thesis of these notes in a single pair of files.** The bug was written once, copied once, and fixed once — in the copy. The original still logs `"type null"` for every unsupported channel type. When you fix something, the next question is always: *where did this come from, and where else did it go?*

**`MediaService.java:43-46` — still open.** Validates `file.getContentType()`, reports `command.contentType()`. They agree today only because `MediaDtoMapper:16` copies one from the other, which is itself the redundancy flagged in chapter 03 §1.

**The rule: the validated expression and the reported expression must be the same expression.** Interpolate the actual value — `"Forum requires expiredTime, got: null"` — and the class of bug disappears, because a wrong message becomes self-evidently wrong.

---

## 7. One fact, one definition

The refresh-token lifetime is defined three times, with three values:

| Where | Value |
|---|---|
| `TokenIssuerAdapter.java:36` — `refreshTtlSec` | 1209600s = **14 days** |
| `JwtUtils.java:73` — stored `expiredTime` | **7 days** |
| `AuthController.java:130` — cookie `maxAge` | **7 days** |

So the JWT claims 14 days, the database row expires at 7, and the cookie expires at 7. On day 8 the client holds a token it believes is valid and `consumeIfValid` rejects it.

The access TTL has the same disease in miniature — `TokenIssuerAdapter.java:35`:

```java
private static final long accessTtlSec = 3600; // 15 min
```

3600 seconds is 60 minutes. The comment is wrong, and it matters: combined with broken revocation (chapter 02 §3), it sets how long a "logged out" session stays live.

**The rule.** A credential has exactly one lifetime. One named constant, read by the signer, the store and the cookie. A comment that disagrees with the code is worse than no comment — reviewers read the comment instead of the number.

---

## 8. `ErrorCode` is the most-churned file in the repo

`git log` shows `ErrorCode.java` modified in **12 commits** — more than any other file. Every feature must edit one central enum.

That coupling isn't automatically wrong; a stable, enumerable error vocabulary is valuable. But the current scheme has no rule:

```java
PASSWORD_INCORRECT("USR_223"),     // not an HTTP status
PASSWORD_NOT_MATCH("USR_224"),
REFRESH_TOKEN_INVALID("AUTH_225"),
PASSWORD_DUPLICATE("USR_225"),     // numeric collision with AUTH_225
VALIDATION_ERROR("VLD_401"),       // returns 422
CHANNEL_NOT_FOUND("CHANNEL_401"),  // returns 404
USER_DUPLICATE("USR_405"),
OK("SVR_200"),                     // a success code, in ErrorCode
```

The numeric suffix *looks* like an HTTP status and isn't one. A client that branches on it branches wrongly.

**The rule.** An application error code is an **opaque, stable identifier**. Encoding a lookalike HTTP status inside it creates a second source of truth that drifts from the first immediately (§1 again). Either make the number always equal the real status, or make it clearly not a status (`USR_PASSWORD_INCORRECT`). Success codes belong in a different type.

---

## 9. Validation annotations that don't do what they look like

`adapters/in/web/dto/request/AddMembersRequest.java:7-8`:

```java
@Schema(name = "UpdateChannelRequest", description = "Request for updating a channel")
public record AddMembersRequest(@Size(min = 1) List<Integer> userIds) {}
```

Two problems.

**`@Size` accepts `null`.** Jakarta Bean Validation explicitly treats `null` as valid for `@Size`, `@Min`, `@Max` and `@Pattern` — presence is a *separate* constraint. So `{"userIds": null}` passes `@Valid` and NPEs at `ChannelService.java:71` (`new HashSet<>(command.userIds())`) → 500. You need `@NotNull` as well.

**No upper bound.** `CreateChannelRequest.java:17` caps at `@Size(min = 1, max = 10)`; this one doesn't. A 100,000-element list is accepted.

**And the `@Schema(name = ...)` is copy-pasted** from `UpdateChannelRequest`, so the two collide in the OpenAPI document — the same defect as `CreateChannelResponse`'s wrong `@Schema` in `lessons.md` #18.

The unbounded-limit sibling is `ChannelController.java:79`:
```java
@RequestParam(defaultValue = "20") int limit,
```
versus the correct `MessageController.java:34`:
```java
@RequestParam(defaultValue = "20") @Min(1) @Max(100) Integer limit,
```

`?limit=-1` → `PageRequest.of(0, 0)` → `IllegalArgumentException` → 500. `?limit=0` → an empty page with `hasMore=true` → an infinite client loop.

---

## 10. Pagination: `cursorId` and `hasMore` must agree

`MessageService.java:76-78`:

```java
boolean hasMore = fetched.size() > command.limit();
List<MessageItem> items = hasMore ? fetched.subList(0, command.limit()) : fetched;
return new MessageList(items, items.getLast().getId(), hasMore);
```

The `limit + 1` technique is correct — that part is right, and `lessons.md` #12 documents it. But the cursor is returned **even when `hasMore` is false**. A client looping `while (cursorId != null)` never terminates on its own; only a client checking `hasMore` works. Two fields encoding the same fact, inconsistently (§1, again).

`ChannelAdapter.loadChannels:41-45` has the identical issue.

**The fix.** `hasMore ? items.getLast().getId() : null`.

---

## Checklist

- [ ] Status line and body always agree
- [ ] No `default ->` in a switch over an enum you own
- [ ] The catch-all handler logs the throwable and returns a correlation id
- [ ] No controller returns `null`
- [ ] Every throw in the core uses the domain hierarchy
- [ ] Validation messages interpolate the actual value
- [ ] Each fact (TTL, count, status) has exactly one definition
- [ ] `@Size`/`@Min` is paired with `@NotNull` where presence is required
- [ ] Every collection parameter from the network has a maximum
- [ ] `cursorId` is null when `hasMore` is false
