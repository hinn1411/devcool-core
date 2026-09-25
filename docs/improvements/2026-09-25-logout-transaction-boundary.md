# Improvements — Logout Spans Two Transactions

**Date:** 2026-09-25
**Scope:** `AuthController.logout`, `LogoutUseCase`, `RefreshTokenService`

Found while moving `@Transactional` off `AuthAdapter` and onto the services that call it (`docs/learning/01-jpa-and-transactions.md` §5). This document records the finding only — nothing here was changed, and it was derived from reading the code, not reproduced against a database.

Context: after that move, each `LogoutUseCase` method is atomic on its own. Logout as a whole is not, because the controller strings two use-case calls together. It was already two transactions before the move (one per adapter method), so the move exposes the gap rather than creating it.

---

## Medium

### 1. Logout revokes the refresh token and bumps the token version in two separate transactions
**File:** `adapters/in/web/controller/AuthController.java:197-199`

```java
tokenRevoker.revokeRefreshToken(refreshToken);      // transaction 1 — marks the refresh token consumed
Integer userId = Integer.valueOf(auth.getName());
tokenRevoker.updateAccessTokenVersion(userId);      // transaction 2 — token_version = token_version + 1
```

Each call enters its own `@Transactional` method on `RefreshTokenService`, so transaction 1 commits before transaction 2 starts.

**Failure modes**

- **Transaction 2 fails after transaction 1 committed** (database error, dropped connection, process killed between the two). The refresh token is revoked but `TOKEN_VERSION` is unchanged, so access tokens already issued stay valid until they expire — bumping the version is what invalidates them early. The request returns 500, so the response that clears the `rt` cookie is never sent and the browser keeps a dead cookie.
- **A retry repairs it.** `revoke` on an already-consumed token returns `false`, which `revokeRefreshToken` only logs (`Cannot revoke token!`), so the second attempt goes on to the version bump. This relies on the client retrying; nothing repairs it otherwise.
- **Transaction 1 fails** (null cookie, malformed JWT). It throws before any write, so there is no partial state.

**Related:** the ordering of the two steps — "a logout revokes the refresh token, then invalidates outstanding access tokens" — is business logic, and it lives in the controller. `CLAUDE.md` says adapters must not contain business logic. That placement is also why the two steps could not share a transaction.

**Fix:** collapse `LogoutUseCase` to one method, for example `logout(String refreshToken, Integer userId)`, implemented in `RefreshTokenService` under a single `@Transactional` that calls both `refreshStore.revoke` and `accessTokenPort.updateVersion`. The controller then makes one call and builds the expired cookie afterwards. The cookie is not a transactional side effect — it is only built after the use case returns — so no `@TransactionalEventListener(phase = AFTER_COMMIT)` is needed.

Keep the current behavior where `revoke` returning `false` logs a warning and continues, so a retried logout still succeeds. Do not turn it into a rollback.

**Test:** a Mockito unit test on `RefreshTokenService` asserting that `updateVersion` is still called when `revoke` returns `false`. There is no test for this service today. Note that `revokeRefreshToken` parses the token with `JwtUtils.jtiFrom`, so such a test needs a real JWT until that dependency moves behind a port.
