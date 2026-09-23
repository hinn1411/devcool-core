# Improvements — Error Code → HTTP Status Mapping

**Date:** 2026-09-23
**Branch:** `master`
**Scope:** `HttpErrorMapper`, `ApiExceptionHandler`, and the domain exceptions that feed them

**Fixed:**

- **`HttpErrorMapper` maps every `ErrorCode`.** Before this change, 16 of 22 codes fell through to `default -> 500`, so errors like a wrong password or a duplicate member returned 500. The `default` branch is gone and the switch is exhaustive, so a new `ErrorCode` with no mapping won't compile.

The items below are still open.

---

## High

### 1. Forbidden responses are sent with HTTP 500
**File:** `src/main/java/com/devcool/adapters/in/web/handler/ApiExceptionHandler.java` (`handleForbiddenRequest`)

The handler builds the response with `ResponseEntity.internalServerError()`, so the status line is **500**. The body still says `FORBIDDEN` / `SVR_403`, which means clients that check the status code see a server error instead of an authorization failure.

**Fix:** use `ResponseEntity.status(HttpStatus.FORBIDDEN)`, or `HttpErrorMapper.toHttpStatus(ErrorCode.FORBIDDEN)` like `handleMaxUploadSize` does.

### 2. Login leaks which usernames exist
**File:** `src/main/java/com/devcool/application/service/AuthenticateUserService.java`

An unknown username throws `UserNotFoundException`, which returns 404 `USR_404`. A wrong password throws `PasswordIncorrectException`, which returns 401 `USR_223`. Because the two responses differ, anyone can check whether a username is registered.

**Fix:** make both cases fail the same way, for example by throwing `PasswordIncorrectException` or a new generic `INVALID_CREDENTIALS` code (401), with the same message. Keep the specific reason in server logs only.

---

## Medium

### 3. `InvalidObjectKeyException` reuses `UNSUPPORTED_MEDIA_TYPE`
**File:** `src/main/java/com/devcool/domain/media/exception/InvalidObjectKeyException.java`

`MediaService.getPresignedUrl` throws this exception when `objectKey` is blank. It carries `UNSUPPORTED_MEDIA_TYPE` (`MEDIA_400`), which now maps to **415**. That's wrong for a `GET` request with a bad query parameter, and it shares an error code with a different failure.

**Fix:** use `VALIDATION_ERROR` (422), or add an `INVALID_OBJECT_KEY` code mapped to 400 in `HttpErrorMapper`.
