# 13 — Integration Testing Exercises

Ten exercises against **real code in this repo**, from the first container to concurrent requests. Read [12 — Integration Testing Guide](12-integration-testing-guide.md) first; each exercise points back to the section it practises.

Four of them are real deliverables, not practice. Exercise 1 **is P1-T11**, and exercises 2, 7 and 8 together **are P1-T12**. When those pass review, tick the tasks in `docs/plans/phases/phase-1-foundation-hardening.md` in the same change.

---

## How this works

1. Start with exercise 1. Every other exercise needs its base classes.
2. Create each test under `src/test/java/`, in the package given, with a name ending in **`IT`** (12 §2).
3. Write the tests **from the spec in the exercise, not from the implementation.** Read the code to learn how to call it and what to seed, not what it should return.
4. Run: `./mvnw -Dit verify -Dit.test=YourIT` (Docker must be running), then `./mvnw spotless:apply`.
5. Ask me: **"review IT exercise N"**. I'll review against [12 §12](12-integration-testing-guide.md#12-review-checklist) plus the exercise's "Done when" list.

### About red tests

The rules are the same as chapter 08. Exercises marked 🐞 have at least one case that should go red if your test follows the spec. I checked each one against the code before marking it.

When a test goes red:
- **Don't** change the expected value to make it pass.
- **Do** decide whether the code is wrong or your reading of the spec is. Write down which, and why, in a comment above the test.
- Leave it red, or mark it `@Disabled("BUG: <one line>")`, and tell me in the review request. Fixing the production code is a separate step on its own branch.

Exercises without 🐞 have no bug I know of. There, the challenge is proving the test is green **for the right reason**: the excluded rows are seeded, the SQL really ran, and the assertions come from the spec.

Collapsed **Hints** blocks are there if you're stuck. Try without them first.

---

## Level 1: Infrastructure

### Exercise 1: The base classes and the smoke IT (P1-T11)

**Target:** the test infrastructure itself, plus `src/test/java/com/devcool/DevCoolApplicationTests.java` (today: an empty class, see 06 §1)
**Files:**
- `pom.xml`: the three test dependencies (12 §3)
- `src/test/resources/application-test.properties`
- `src/test/java/com/devcool/support/` → the container declaration, `AbstractIntegrationTest` (`@SpringBootTest` + MockMvc) and `AbstractJpaIT` (`@DataJpaTest`)
- `src/test/java/com/devcool/DevCoolApplicationIT.java` (replaces `DevCoolApplicationTests`)

**Practises:** 12 §2 (Failsafe naming), §3 (singleton container, `@ServiceConnection`, pgvector), §4 (context cache, the `application.properties` trap), §5 (`NON_TEST`)

**Spec.**
- One `pgvector/pgvector:pg16` container serves every IT in the JVM run.
- The full application context starts against it with the `test` profile, and **no environment variables** (no `JWT_*`, no AWS credentials).
- Flyway builds the schema from an empty database, and Hibernate's `validate` accepts it. That's P1-T01's "Definition of Done" turned into a test.
- `./mvnw test` doesn't start Docker.

**Required cases (in `DevCoolApplicationIT`)**
- The context loads. Assert something concrete, such as a bean you need later (`MockMvc`, `JdbcTemplate`) is present. Don't leave an empty method.
- `flyway_schema_history` holds versions `1` and `2`, both with `success = true`, and nothing else.
- The `vector` extension is **available** in the container (`pg_available_extensions`). This proves the image is pgvector, not plain postgres. (Enabling it is P4's migration, not yours.)
- `ix_message_channel_id_id` exists (`pg_indexes`), so V2 really ran.

**Done when**
- `./mvnw test` runs zero ITs and starts no container. `./mvnw -Dit verify` runs `DevCoolApplicationIT`.
- The container is declared once and shared by both base classes.
- Subclasses of the bases carry no configuration annotations
- Running two IT classes logs **one** container start, and one Spring context per base class (count the `Started DevCoolApplication…` / Testcontainers log lines)
- `DevCoolApplicationTests.java` is gone (renamed), and nothing in `src/test/resources` is called `application.properties`

**Stretch:** temporarily break the schema: add a `@Column` to an entity with no migration behind it. Which test fails, with what message, and how long did it take to find out? That's the safety net P1-T01 promised.

<details><summary>Hints</summary>

- `DockerImageName.parse("pgvector/pgvector:pg16").asCompatibleSubstituteFor("postgres")`
- The JWT secrets must be Base64 and decode to ≥ 32 bytes (`TokenIssuerAdapter`). Use `head -c 32 /dev/urandom | base64`.
- `jdbc.queryForList("select version, success from flyway_schema_history order by installed_rank")`
- If the context fails on a missing bean or property, read the *first* "Caused by". It's usually a placeholder.
</details>

---

## Level 2: Persistence adapters (`@DataJpaTest`)

### Exercise 2: Message history cursor query (P1-T12, part 1)

**Target:** `MessageAdapter.findMessages` → `MessageRepository.findByChannelId` (`src/main/java/com/devcool/adapters/out/persistence/message/MessageAdapter.java:36`, `MessageRepository.java:12-26`)
**Test file:** `src/test/java/com/devcool/adapters/out/persistence/message/MessageAdapterIT.java`
**Practises:** 12 §6 (flush/clear), §7 (test through the port, seed the excluded rows, order as contract)

**Spec** (the `MessagePort.findMessages(channelId, cursorId, size)` contract, which `MessageService` relies on for its `limit + 1` trick)
- Returns at most `size` messages of **that channel only**
- Newest first: **descending id**
- `cursorId == null` → start from the newest message
- `cursorId != null` → only messages with `id < cursorId` (**exclusive**: the cursor message itself is never repeated on the next page)
- Soft-deleted messages (`deleted_time` set) are never returned
- Each item carries the sender's id, name and avatar, plus the message's content type, content and created time
- The result is fully usable after the transaction ends: no lazy loading left to do

**Required cases**
- Null cursor on a channel with more messages than `size` → exactly the `size` newest, in descending order
- Cursor in the middle → strictly older messages only, and the cursor's own message is absent
- Cursor older than every message → empty list
- A message in **another channel** with an id inside the page's range is excluded
- A **soft-deleted** message inside the page's range is excluded, and the page still holds `size` items when enough exist
- `size` exactly equal to the remaining rows → all of them, no error
- An image message: the sender fields are correct. Find out what `MessageItem` carries for media, and assert it, or record that it carries nothing.

**Done when**
- The test calls **`MessagePort`**, not the repository (12 §7)
- Seeding is followed by `flush()` + `clear()`, so the query reads Postgres and not the persistence context
- Order is asserted with `containsExactly`, using ids captured from the seed (no hard-coded ids)
- The excluded rows (other channel, soft-deleted) sit **between** included ids, so a missing filter would show

**Stretch (two parts)**
1. **Statement count**: one call → exactly one SQL statement, whatever the page size (12 §7, Hibernate statistics).
2. **The plan**: `EXPLAIN` the query `findByChannelId` produces. Seed enough rows, or `SET enable_seqscan = off`, and assert that the plan uses `ix_message_channel_id_id`. If it doesn't, explain why.

<details><summary>Hints</summary>

- `@Import({MessageAdapter.class, MessageMapperImpl.class, MediaMapperImpl.class})`. The MapStruct implementations are generated into `target/generated-sources` with the `Impl` suffix.
- `MessageEntity` needs a persisted `UserEntity` and `ChannelEntity` (`optional = false`). A small fixture helper keeps the tests readable.
- To soft-delete, set `deletedTime` on the entity before persisting, or `UPDATE` it with `JdbcTemplate`.
</details>

---

### Exercise 3: Refresh tokens are single use

**Target:** `AuthAdapter` as `RefreshTokenPort` (`src/main/java/com/devcool/adapters/out/persistence/auth/AuthAdapter.java:47-70`), backed by the native SQL in `RefreshTokenRepository.java:11-36`
**Test file:** `src/test/java/com/devcool/adapters/out/persistence/auth/AuthAdapterIT.java`
**Practises:** 12 §1 (native SQL), §6 (`now()` inside a test transaction), §9 (concurrency)

**Spec** (rotation: `RefreshTokenService.refresh` consumes the presented token before it issues a new pair, so a refresh token can be exchanged **once**)
- `consumeIfValid(jti)` returns `true` for a stored, unconsumed, unexpired token, and marks it consumed
- Calling it again for the same `jti` returns `false`. That's how a replayed (stolen) token is detected.
- An expired token → `false`, and it stays unconsumed
- An unknown `jti` → `false`
- `revoke(jti)` → `true` once, then `false`. After a revoke, `consumeIfValid` → `false`.
- `deleteOldRefreshTokens(userId)` removes **every** token of that user and **none** of another user's

**Required cases:** one per bullet. Assert the return value **and** the stored `consumed_time` (null vs set).

**Done when**
- Tokens are seeded through `RefreshTokenPort.store`, so the mapper is exercised too. The expired one may need `JdbcTemplate` or an `expiredTime` in the past.
- Your review request answers: in a rolled-back `@DataJpaTest`, what does `now()` return on the second `consumeIfValid` call, and could that make one of these tests pass for the wrong reason?

**Stretch: the race that rotation must survive.** Two threads call `consumeIfValid` for the **same** `jti` at the same moment. Exactly one must win. Run it 20 times with a `CountDownLatch` (12 §9). This needs committed data, so the stretch goes in an `AbstractIntegrationTest` subclass, not in the `@DataJpaTest`. Then explain **why** exactly one wins under `READ COMMITTED`, without any explicit lock in the code. What does Postgres do when the second `UPDATE … WHERE consumed_time IS NULL` hits a row the first one has already locked?

<details><summary>Hints</summary>

- `AuthAdapter` also needs `UserMapperImpl` and `RefreshTokenMapperImpl`. Check its constructor.
- The port takes the **hashed** `jti`. For the adapter test, any string is fine as long as you use the same one throughout.
</details>

---

### Exercise 4: Channel list and membership queries

**Target:** `ChannelAdapter.loadChannels` / `findAccessInfo` / `increaseTotalMembers` (`ChannelAdapter.java:34-87`) and `MemberAdapter.findRoleOfMember` (`MemberAdapter.java:39`)
**Test files:** `src/test/java/com/devcool/adapters/out/persistence/channel/ChannelAdapterIT.java`, `.../member/MemberAdapterIT.java`
**Practises:** 12 §7 (the adapter's own `limit + 1` logic), §6 (seeding graphs)

**Spec: `loadChannels(memberId, cursor, limit)`**
- Only channels where `memberId` has a membership row
- Newest first (descending id). `cursor` is exclusive, the same as messages.
- At most `limit` items. `hasMore` is true **only** if another page exists. `cursorId` is the id of the last item returned, or `null` for an empty page.
- A channel appears **once**, however many other members it has

**Spec: the rest**
- `findAccessInfo(id)` → the channel's type and current `totalOfMembers`; empty for an unknown id
- `findRoleOfMember(channel, user)` → the role, or empty for a non-member. **A member of a different channel is a non-member here.**
- `increaseTotalMembers(id, n)` adds `n` to the stored count and returns `true`; unknown id → `false`

**Required cases**
- Pages of a user who is in 5 channels, with `limit = 2`: walk all pages with the returned cursor and assert the full sequence, plus `hasMore` on each page.
- `limit` equal to the number of channels → `hasMore = false` (the boundary 07 §9 talks about)
- A channel with 4 members appears once in the creator's list
- Channels the user is **not** in are absent, even when their ids fall between the user's channels
- Role lookups for creator, leader and member, and for a user who's a member elsewhere

**Done when**
- The walk-all-pages test ends on a page with `hasMore = false`, and the union of all pages equals the user's channels exactly, with no duplicates and none missing
- `increaseTotalMembers` is asserted by re-reading after `flush()` + `clear()`, not through the cached entity

---

### Exercise 5: Constraints and what the client sees 🐞

**Target:** the unique constraints in `V1__baseline.sql:31-32,79` and their translation in `ApiExceptionHandler.handleDataIntegrityViolation` (`src/main/java/com/devcool/adapters/in/web/handler/ApiExceptionHandler.java:102-116`)
**Test files:**
- part A: `src/test/java/com/devcool/adapters/out/persistence/member/MemberConstraintIT.java` (`@DataJpaTest`)
- part B: `src/test/java/com/devcool/adapters/in/web/controller/RegisterRaceIT.java` (`AbstractIntegrationTest`)

**Practises:** 12 §6 (Trap 1: the INSERT that never runs), §9 (check-then-act)

**Spec**
- A user can be a member of a channel **once** (`uk_member_channel_user`)
- Username and email are unique (`uk_username`, `uk_email`)
- Registering with a username or email that's already taken is a **client** error: `409`, with `USR_410` / `USR_409`. That holds whether the duplicate was detected by the service's `exists…` check or by the database constraint: the client can't tell the two apart, and shouldn't have to.

**Required: part A (the constraint exists and is named)**
- Insert a duplicate membership row → `DataIntegrityViolationException`, and its most specific cause mentions `uk_member_channel_user`. That name is what `ApiExceptionHandler` matches on, so this test protects the 409 against a future migration that renames the constraint.
- Write it once with `save(...)` and once with `saveAndFlush(...)`. Keep only the correct one, and explain the other in a comment.

**Required: part B (the race the service check can't stop)**
- 20 iterations: two `POST /api/v1/auth/register` requests start together, with **different usernames** and the **same email**.
- Invariant 1: exactly one row with that email exists afterwards.
- Invariant 2: the statuses are exactly `{201, 409}`, and the 409 body has `code = "USR_409"`.

**Done when**
- Part B is not `@Transactional`, uses a `CountDownLatch`, and cleans the tables between iterations (or uses a fresh email per iteration)
- Your review request explains, for part B, **where** the losing request's error is raised (which line, which phase of the transaction), and which handler method turns it into the status you saw

**Stretch:** propose the fix. Which layer should translate `uk_email` / `uk_username` into `EmailAlreadyUsedException` / `UsernameAlreadyUsedException`: the persistence adapter, or `ApiExceptionHandler`? Use the hexagonal rule from CLAUDE.md ("Domain exceptions: thrown from the application service layer, not from controllers or adapters") to argue for one. Note that it's in tension with "where can the constraint error actually be caught?"

<details><summary>Hints</summary>

- Look at what `UserService.register` does **between** `existsByEmail` and `save` (`UserService.java:99-104`, `buildUser`). That gap is why the race reproduces reliably here.
- In part A, a `MemberEntity` needs managed `UserEntity` and `ChannelEntity` references.
</details>

---

### Exercise 6: `@Modifying` and the persistence context 🐞

**Target:** `ChannelAdapter.update` → `ChannelRepository.updateChannelInfo` (`ChannelRepository.java:26-37`) and `ChannelAdapter.findById` (`ChannelAdapter.java:29`)
**Test file:** `src/test/java/com/devcool/adapters/out/persistence/channel/ChannelUpdateIT.java`
**Practises:** 12 §6 (Trap 2: reading your own cache)

**Spec** (the `ChannelPort` contract, as any caller would assume it)
- After `update(channel)` returns `true`, `findById(id)` returns the new name, type and expiry, **including later in the same transaction**
- `update` on an unknown id returns `false`
- `update` changes only name, type and expiry. Creator, leader, boundary and member count are untouched.

**Required cases**
- Persist a channel, **load it once through `findById`** (as a service that checks something first would), call `update`, then `findById` again **in the same transaction**, with no `clear()`
- The same sequence, but with `flush()` + `clear()` before the second `findById`
- Unknown id → `false`
- Untouched fields stay the same: re-read them after `clear()`

**Done when**
- Your comment above the red case explains what the persistence context returned, and why the `UPDATE` didn't reach it
- Your review request answers: **does any production code path hit this today?** Trace the callers of `ChannelPort.update` and of `findById` in the same transaction. A latent bug and a live one need different urgency, so say which this is.

**Stretch:** fix it in the repository with `@Modifying(clearAutomatically = true, flushAutomatically = true)`. What does each flag protect against, and what does `clearAutomatically` cost a caller that has *other* unsaved changes in the same transaction?

---

## Level 3: Full stack (`@SpringBootTest` + MockMvc + real tokens)

### Exercise 7: Channel endpoints authorization matrix (P1-T12, part 2)

**Target:** `PATCH /api/v1/channels/{id}`, `POST /api/v1/channels/{id}/members` and `GET /api/v1/channels` through `SecurityConfig`, `JwtAuthFilter`, `ChannelController`, `ChannelService.requireAllowed` (`ChannelService.java:127-151`) and `ChannelPermissionPolicy`
**Test file:** `src/test/java/com/devcool/adapters/in/web/controller/ChannelControllerAuthzIT.java`
**Practises:** 12 §8 (real tokens), 07 §3 (`@MethodSource` for a matrix)

**Spec** (phase-1 design notes + `ChannelPermissionPolicy`)

| Caller | `PATCH` | `POST …/members` |
|---|---|---|
| no `Authorization` header | 401 | 401 |
| a malformed or wrongly-signed token | 401 | 401 |
| a token issued **before** that user's logout | 401 | 401 |
| authenticated non-member | 403 `MEMBER_404` | 403 `MEMBER_404` |
| LOUNGE member / creator | 200 | 200 |
| FORUM plain member | 403 `SVR_403` | 403 `SVR_403` |
| FORUM leader / creator | 200 | 200 |
| PRIVATE_CHAT participant | 200 | 400 `CHANNEL_400` (a private chat can't gain members) |
| channel id that doesn't exist | 404 `CHANNEL_401` | 404 `CHANNEL_401` |

Also: `GET /api/v1/channels` without a token → 401 (this is the P1-T09 fix), and with a token → only the caller's channels.

**Required cases:** every cell. The "authenticated" rows are one parameterized test per endpoint, driven by `@MethodSource` (channel type × role → expected status + code).

**Done when**
- Every token is **real**, from `/login` or `TokenIssuerPort.issue`. No `.with(user(...))` anywhere in this class.
- Every rejected write is followed by a DB check that **nothing changed**: name unchanged, member count unchanged, no new member rows. A 403 that still wrote the row is a failure.
- Every accepted write is checked in the DB too
- Channels are created in setup and their ids captured. No hard-coded ids.

**Stretch:** add the case "a member of channel A calls PATCH on channel B". Which row of the table is it? Then think about why IDOR bugs (#4 and #5 in the audit) are usually *this* shape, and not "no token at all".

<details><summary>Hints</summary>

- The pre-logout token: get a token, call `POST /auth/logout` with it (and the cookie), then reuse it. Or call `AccessTokenPort.updateVersion(userId)` directly, if you want this test independent of the logout endpoint.
- Setting up a FORUM with a leader through `POST /api/v1/channels` exercises the creation strategy too. That's fine here, and it means the setup itself proves the creation path works end to end.
</details>

---

### Exercise 8: The refresh-cookie round trip (P1-T12, part 3)

**Target:** `AuthController` login / refresh_token / logout / password (`AuthController.java:119-226`), `RefreshTokenService`, `AuthenticateUserService`, `UserService.change`
**Test file:** `src/test/java/com/devcool/adapters/in/web/controller/AuthFlowIT.java`
**Practises:** 12 §8 (cookies by hand, assert the attributes, assert the DB), §6 (Option B: commit for real)

**Spec** (P1-T07/T08; `SameSite=Strict` from ADR-0011)
- Login → 200, an access token in the body, and `Set-Cookie: rt=…; Path=/api/v1/auth; HttpOnly; Secure; SameSite=Strict` with a 7-day max age
- Refresh with that cookie → 200, a **new** access token, and a **rotated** cookie (a different value)
- Refresh **again with the old cookie** → 401 `AUTH_225` (replay detected)
- Refresh with no cookie → 401 `AUTH_225`, not 500
- Logout with the access token + current cookie → 204, plus a `Set-Cookie` that expires `rt` (`Max-Age=0`). Afterwards:
  - the **old access token** is rejected (401) on `GET /api/v1/auth/profile`
  - the logged-out refresh cookie → 401 on refresh
- Change password → 204. Afterwards, every refresh token of that user is gone, and the access token used for the call is rejected.

**Required cases:** each bullet as its own test, starting from a freshly registered user. The rotation, replay and logout tests are where the bugs used to be.

**Done when**
- The class extends `AbstractIntegrationTest` and is **not** `@Transactional` (each request commits, as in production)
- Cookies are carried explicitly between requests, and the attributes are asserted on the raw `Set-Cookie` header (12 §8)
- After logout and after a password change, the test also checks the DB: `app_user.token_version` went up, and the `refresh_token` rows are consumed or deleted
- A small helper keeps each test focused on its own step (`register()`, `login()` → a record of access token + cookie value)

**Stretch:** the old Path bug (`/api/v1/auth/refresh` vs `/refresh_token`, audit #7) would *not* have been caught by this test without the attribute assertion. Explain why in two sentences, using what MockMvc does and doesn't do with cookies.

---

### Exercise 9: Concurrent add-member 🐞

**Target:** `ChannelService.addMember` (`src/main/java/com/devcool/application/service/channel/ChannelService.java:88-121`)
**Test file:** `src/test/java/com/devcool/application/service/channel/AddMemberConcurrencyIT.java`
**Practises:** 12 §9 (latch, iterations, invariants), §6 (no test transaction)

**Spec**
- A lounge holds **at most 11 people**: the creator plus ten (`ChannelService.java:37-38`, design doc 03)
- A user is a member of a channel at most once
- `channel.total_of_members` always equals the number of `member` rows for that channel

**Required cases** (each repeated ≥ 20 times, with fresh channels per iteration)
- **Same user, twice at once**: two requests from the lounge creator add the *same* new user together.
  - Invariant: exactly one membership row for that user, and the counter equals the number of rows.
  - Expected statuses: `{200, 409}`.
- **The cap**: a lounge with 9 people. Two requests at once each add **2 different** new users. Applied one after the other, the first succeeds (→ 11) and the second must be rejected (→ 13 > 11).
  - Invariant: the number of people never exceeds 11, and the counter equals the number of rows.
  - Expected statuses: `{200, 400}`.

**Done when**
- Both requests go through MockMvc with real tokens, on two threads released by one `CountDownLatch`
- The invariants are checked with SQL counts (`JdbcTemplate`), not through the API
- Each red case has a comment naming the two lines that form the check-then-act, and what Postgres does (or doesn't do) between them

**Stretch:** propose and compare two fixes for the cap:
1. A conditional atomic update, `UPDATE channel SET total_of_members = total_of_members + :n WHERE id = :id AND total_of_members + :n <= :max`, with "0 rows updated" meaning rejected
2. `SELECT … FOR UPDATE` on the channel row at the start of `addMember`

Which one keeps the rule in the domain, and which one moves it into SQL? What does each do to throughput on a busy channel? (Phase 3's per-channel `seq` uses a row lock. Is that an argument for option 2?)

<details><summary>Hints</summary>

- Before you write the test, read `requireAllowed` and `addMember` in order, and note **which value each request reads** for `totalOfMembers` and **when**. The bug is visible on paper. The IT proves it happens.
- For the duplicate-user case, find which statement makes the second request *wait*, and on what.
</details>

---

### Exercise 10 (bonus): The `@Transactional` test trap

**Target:** your own tests from exercises 5, 6 and 9
**Test file:** `src/test/java/com/devcool/support/TransactionalTrapIT.java` (a scratch file. Delete it after the review if you like; the note is what matters.)
**Practises:** 12 §6 (all three traps)

**Tasks**
1. Copy the "same user, twice at once" case from exercise 9 into a class annotated with `@Transactional`. Run it, and record what the two workers see and why.
2. Copy the part-A constraint test from exercise 5, using `save(...)` without a flush. Record what happens and why.
3. Write a test in a `@Transactional` `@SpringBootTest` that calls `PATCH /api/v1/channels/{id}` through MockMvc, then reads the channel through `ChannelPort.findById`. Compare it with the same test without `@Transactional`. Which version is telling the truth, and which production behaviour does the other one hide?
4. In a comment block at the top of the class, write **three rules** for when an IT in this repo may be `@Transactional`, one sentence each.

**Done when:** I can read your three rules and either agree, or point at a test in this repo that breaks one.

---

## Progress

Fill this in as you go.

| # | Exercise | Level | Task | 🐞 | Status | Reviewed |
|---|---|---|---|---|---|---|
| 1 | Base classes + smoke IT | Infra | **P1-T11** | | ☐ | ☐ |
| 2 | Message cursor query | Persistence | **P1-T12** | | ☐ | ☐ |
| 3 | Refresh tokens single use | Persistence | | | ☐ | ☐ |
| 4 | Channel list + membership | Persistence | | | ☐ | ☐ |
| 5 | Constraints → client status | Persistence + flow | | ✓ | ☐ | ☐ |
| 6 | `@Modifying` vs the cache | Persistence | | ✓ | ☐ | ☐ |
| 7 | Channel authz matrix | Full stack | **P1-T12** | | ☐ | ☐ |
| 8 | Refresh-cookie round trip | Full stack | **P1-T12** | | ☐ | ☐ |
| 9 | Concurrent add-member | Full stack | | ✓ | ☐ | ☐ |
| 10 | `@Transactional` trap (bonus) | Design | | | ☐ | ☐ |

**Suggested path:** 1 → 2 → 7 → 8 finishes P1-T11 and P1-T12. Then 5 → 9 for concurrency, and 6 → 10 for transactions. 3 and 4 are for repetition.

**Note:** the specs cite code as of `master` at `2e33734` (2026-10-04). If P1-T18 (channel update validation) lands first, exercise 7's `PATCH` bodies must satisfy its rules. Tell me and I'll update the specs.
