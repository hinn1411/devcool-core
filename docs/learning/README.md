# Learning Notes — DevCool

Written from a full audit of all 185 source files on `refactor/message-creation-improvements` (2026-09-21).

## How this differs from `docs/improvements/`

`docs/improvements/lessons.md` is **incident-driven**: 21 rules from reviewing one branch. Useful, but it teaches 21 facts.

These notes are **concept-driven**. Each entry explains the mechanism underneath a bug, so you recognise the whole *class* next time instead of remembering one instance. Where a lesson already exists in `lessons.md`, these link to it by number rather than repeat it.

Every claim cites `file:line` and quotes the real code. Nothing here is a hypothetical.

---

## Fix these first

The audit found live security holes, not just teaching material. These are not "learning topics" — they are open in `master` today.

| # | Issue | Where | Effect |
|---|---|---|---|
| 1 | **Every user's password hash is downloadable** | `UserController.java:24-31` | Returns the raw domain `User`. Any logged-in user walks `/api/v1/users/1..N` and harvests every bcrypt hash, email, role and `tokenVersion`. |
| 2 | **Login returns the caller's plaintext password** | `PasswordIncorrectException.java:9` → `ApiExceptionHandler.java:27` | A failed login echoes the submitted password in `details.password` — into the response body, proxy logs and APM traces. |
| 3 | **Logout does not log anyone out** | `User.java:40-42` | `tokenVersion >= currentVersion`. Logout sets DB to 4, the old token claims 3, `4 >= 3` passes. Access tokens stay valid for their full TTL — which is 3600s, not the 15 min the comment claims. |
| 4 | **Any user can join any channel** | `ChannelController.java:66-75` | No `Authentication` parameter. POST your own id to any `channelId`. This one gap defeats the three membership checks elsewhere that are written correctly. |
| 5 | **Any user can modify any channel** | `ChannelController.java:55-64` | Same cause. Rename, re-type or set an expiry on any channel by id. |
| 6 | **Password change silently does nothing** | `AuthController.java:140-144` | `return null;` from a `@RestController` makes Spring send **HTTP 200, empty body**. The backing `UserAdapter.updatePassword:60-64` is `return false;`. |
| 7 | **Refresh and logout are broken for browsers** | `AuthController.java:129` | Cookie `path` is `/api/v1/auth/refresh`; the endpoint is `/refresh_token`. Under RFC 6265 the browser never sends the cookie → `MissingRequestCookieException` → 500. |
| 8 | **Production cannot start against a fresh database** | `application-ecs.properties:7,10` | `ddl-auto=validate` with Flyway disabled and zero migration files. Nothing in the repo can create the schema it validates. |

Numbers 1, 2 and 3 are the ones to fix today. Number 8 blocks deployment.

Two things that are *not* as bad as they look, because accuracy matters:

- `PasswordNotMatchException` has the same plaintext-password flaw but is constructed nowhere. Latent, not live.
- The `// Validate member permission later` comment in `WsSubscribeService` looks like a missing membership check. It isn't — membership *is* verified at lines 40-50. The comment refers to role/status checks.

---

## The one idea behind all of it

The striking pattern across ~60 findings is not that techniques are missing. It's that **the correct technique is almost always already in this repo, applied once, and not generalised.**

| You did this correctly… | …and not here |
|---|---|
| `@Min(1) @Max(100)` on `MessageController.java:34` | `ChannelController.java:79` — unbounded `limit` |
| `@Enumerated(EnumType.STRING)` on six enum columns | `AuthProviderEntity.java:21` — defaults to ORDINAL |
| DTO projection in `ChannelRepository.findChannelPageByMemberId` | The message list still hydrates 100 password hashes per page |
| Targeted `@Modifying` in `UserRepository.updateTokenVersion` | `updateLoginTime` merges the whole entity and can un-revoke a session |
| `GetProfileResponse` maps `User` → DTO | `UserController` returns `User` raw |
| Membership checked in three read paths | Absent at the endpoint that *grants* membership |
| `private` constructor on `JwtUtils`, `HttpErrorMapper` | `HashUtils` — missing |
| `${DB_PASSWORD}` in `application-ecs.properties` | `application-local.properties:8` — password committed |

So the gap isn't knowledge. It's that each fix was learned as a **local patch** rather than a **rule**, so it stayed where it was first applied.

That's why every chapter here ends with **"Where else this applies"**. Reading the fix is the easy half; finding its siblings is the skill.

---

## The chapters

| File | Core question |
|---|---|
| [01 — JPA & Transactions](01-jpa-and-transactions.md) | When does your code actually hit the database, and what is one atomic operation? |
| [02 — Security & Authorization](02-security-and-authorization.md) | Who is asking, are they allowed, and what leaves the process? |
| [03 — Hexagonal Architecture](03-hexagonal-architecture.md) | Which way do the dependency arrows point, and what enforces that? |
| [04 — Concurrency & Realtime](04-concurrency-and-realtime.md) | What happens when two threads and a long-lived connection are involved? |
| [05 — API & Error Design](05-api-and-error-design.md) | Your errors are an API. What contract do they offer? |
| [06 — Testing & Verification](06-testing-and-verification.md) | Why did none of the above get caught? |
| [07 — Unit Testing Guide](07-unit-testing-guide.md) | What does a good unit test look like, and how do you build one with JUnit 5, Mockito, AssertJ and `@WebMvcTest`? |
| [08 — Unit Testing Exercises](08-unit-testing-exercises.md) | Twelve hands-on exercises against real classes here, several of which hide live bugs. |
| [09 — Classic vs Mockist](09-test-schools.md) | What do the two testing schools' strengths and weaknesses look like when the code actually changes? |

Suggested order: **02 → 06 → 01 → 04 → 05 → 03.** Security first because it is live; testing second because it explains why everything else survived this long.

07 and 08 are the practical follow-up to 06: read 07, then work through 08 and ask for a review of each exercise.

---

## What the audit got right about this codebase

Worth stating, because a list of 60 findings is misleading on its own:

- `ChannelRepository.findChannelPageByMemberId` is genuinely good — DTO projection, non-fetch join, keyset cursor, deterministic ordering, pagination pushed into SQL.
- `MediaService.buildMediaKey` uses a UUID rather than the client filename, which is why there is no path-traversal bug in the upload path.
- `local.env` holds real JWT secrets, is correctly gitignored, and has never been committed.
- `handleGeneric` does not leak stack traces to clients.
- `MediaServiceTest` and `S3StorageAdapterTest` are well-written: `ArgumentCaptor` assertions on the port contract, `verifyNoInteractions` on negative paths, real boundary cases. You *can* write good tests — which makes chapter 06 a question of where you aimed them, not whether you know how.
- `Instant` is used over `LocalDateTime` essentially everywhere.
- The domain layer is 81 files with one framework import. The discipline is real; chapter 03 is about the three places it leaks, not about starting over.

---

## A note on how this was produced

Findings were gathered by scanning the full tree, then **every High and Critical claim was re-verified by reading the source directly** before it was written down. That mattered: it removed two false alarms (the `WsSubscribeService` comment, and `PasswordNotMatchException` being live), and it corrected an earlier, too-generous claim of mine that the codebase had only one architectural violation.

If a statement here disagrees with the code, the code wins — tell me and I'll fix the doc.
