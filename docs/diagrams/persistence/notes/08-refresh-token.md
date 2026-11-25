# 08 — Refresh-token rotation (notes)

Diagram: [../08-refresh-token.md](../08-refresh-token.md)

## Problem
A stolen refresh cookie must stop working once the real user refreshes. Two tabs refreshing at the same moment must not both get new tokens from one old token.

## Without it
- **`SELECT consumed_time` → check in Java → `UPDATE`:** both tabs read "unconsumed" before either writes (**TOCTOU**), so one token spawns two sessions. A replayed stolen token races the real user and can win.
- **Raw jti stored:** a DB leak hands out valid sessions.

## Mechanism
```sql
-- RefreshTokenRepository.consumeIfValid
UPDATE refresh_token
   SET consumed_time = now()
WHERE jti = :jtiHash
   AND consumed_time IS NULL
   AND expired_time > now()
```
`RefreshTokenService.refresh`, in one transaction: verify the JWT signature → hash the jti (SHA-256) → **consume (1 row or 401)** → load the user → rotate (a new access + refresh pair) → store the new token's hash. If anything after the consume fails, the rollback un-consumes it, so the user isn't logged out by a server error.

Logout revokes the same way (`revoke` = `consumed_time IS NULL` guard) and bumps `app_user.token_version`, which kills outstanding access tokens.

## DB concepts
- **Check and write in one statement.** The `WHERE` is the check, and the row count is the answer. It's atomic without an explicit lock or a higher isolation level.
- **READ COMMITTED re-check** (as in 03 and 04): the second UPDATE waits on the row lock, then evaluates `consumed_time IS NULL` against the committed row → 0 rows.
- **Atomicity across consume + issue.** Old token consumed and new token stored commit together. There's never a moment with zero or two valid tokens for one login.
- **Hashing at rest.** The PK is `SHA-256(jti)`. A fast hash is right here: the jti is a high-entropy random value, not a password, so bcrypt's slowness buys nothing.

## Trade-offs
- **Reuse detection** (a consumed token presented again → revoke the whole token family) isn't implemented. Today reuse is just a 401. It's the next step for theft detection.
- **The concurrent-refresh race is safe by construction, not by test.** `AuthFlowIT` covers rotation, reuse after rotation and logout sequentially. A 20-thread test like the others would close that gap.
- `refresh_token.user_id` is VARCHAR without an FK (00).

## Interview Q&A
- **Why rotate refresh tokens?** It limits a stolen token to one use and makes theft detectable (reuse ⇒ two holders).
- **How do you make "use once" race-free?** A conditional UPDATE with the precondition in the WHERE, then read the affected-row count.
- **Why SHA-256 and not bcrypt for tokens?** Tokens are random with 128+ bits of entropy, so brute force is infeasible anyway. Slow hashing only matters for low-entropy secrets like passwords.

## Blog angle
**"Single-use tokens with one SQL statement."** Hook: the TOCTOU bug in most refresh-token tutorials.

## Code
- [`RefreshTokenService.refresh`](../../../../src/main/java/com/devcool/application/service/RefreshTokenService.java#L36)
- [`RefreshTokenRepository.consumeIfValid`](../../../../src/main/java/com/devcool/adapters/out/persistence/auth/repository/RefreshTokenRepository.java#L22)
- [`Sha256TokenHashAdapter`](../../../../src/main/java/com/devcool/adapters/out/crypto/Sha256TokenHashAdapter.java)
- Test: [`AuthFlowIT`](../../../../src/test/java/com/devcool/adapters/in/web/controller/AuthFlowIT.java) (`validCookie_returnsNewAccessTokenAndRotatedCookie`, `oldCredential_isRejected`, `rejectedCookie_isUnauthorizedAndChangesNothing`)
