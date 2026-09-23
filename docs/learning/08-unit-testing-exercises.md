# 08 — Unit Testing Exercises

Twelve exercises against **real code in this repo**. They go from pure JUnit to `@WebMvcTest`. Read [07 — Unit Testing Guide](07-unit-testing-guide.md) first; each exercise points back to the section it practises.

---

## How this works

1. Create the test class under `src/test/java/`, **in the same package as the class under test** (the path is given in each exercise).
2. Write the tests **from the spec in the exercise, not from the implementation.** Read the code to learn *how to call it*, not *what it should return*.
3. Run it: `./mvnw test -Dtest=YourTestClass`, then `./mvnw spotless:apply`.
4. Ask me: **"review exercise N"**. I'll review against the checklist in [07 §13](07-unit-testing-guide.md#13-review-checklist), plus the exercise's own "Done when" list.

### About red tests

Some of these classes contain **real bugs**. Exercises marked 🐞 have at least one case that *should* go red if your test follows the spec. I won't say which case.

When a test goes red:
- **Don't** change the expected value to make it pass. That turns a bug report into a regression lock.
- **Do** decide: is the *code* wrong, or is *my understanding of the spec* wrong? Write down which, and why, in a comment above the test.
- Leave it red, or mark it `@Disabled("BUG: <one line>")`, and tell me in the review request. Fixing the production code is a separate, optional step (do it on its own branch).

Exercises without 🐞 have no bug I know of. A green run is the expected outcome, and the challenge is proving it's *correctly* green (strong assertions, all branches).

Collapsed **Hints** blocks are there if you're stuck. Try without them first.

---

## Level 1: Easy (pure JUnit, no mocks)

### Exercise 1: Token version check 🐞

**Target:** `User.isTokenVersionValid` in `src/main/java/com/devcool/domain/user/model/User.java:40`
**Test file:** `src/test/java/com/devcool/domain/user/model/UserTest.java`
**Practises:** 07 §3 (`@ParameterizedTest`, `@CsvSource`), §9 (boundaries, spec first)

**Spec.**
- Every user has a `tokenVersion` stored in the DB.
- Each access token carries the version that was current **when it was issued**.
- Logout and password change **increment** the user's DB `tokenVersion`, which revokes every token issued before.
- A token is valid **only if its version equals the user's current DB version**.

How it's called: `JwtAuthFilter.java:107-109` extracts the version from the token and passes it as the `currentVersion` argument. So in `isTokenVersionValid(x)`, `this.tokenVersion` is the DB value and `x` is the token's claim. (The parameter name is misleading. Noticing that is part of the exercise.)

**Required cases**
- Token version equals DB version → valid
- Token issued before a logout (token version is one behind the DB version) → invalid
- Token several versions behind → invalid
- Token version *ahead* of the DB version (it can't happen legitimately, e.g. a forged token or a DB rollback) → decide the right answer from the spec, and justify it

**Done when**
- One `@ParameterizedTest` with a `name = "..."` pattern so every row is readable in the report
- The expected column is derived from the spec above, not from the `>=` in the code

**Stretch:** what happens if `tokenVersion` is `null` (a user created before the column existed)? Write the test that shows it, and say what you think the behaviour *should* be.

<details><summary>Hints</summary>

- `User.builder().tokenVersion(4).build()` is enough; no other fields are needed.
- `@CsvSource({"4, 4, true", ...})` with parameters `(int dbVersion, int tokenClaim, boolean expected)`.
- Chapter 06 §4 has a table row about this method.
</details>

---

### Exercise 2: Error code → HTTP status mapping 🐞

**Target:** `HttpErrorMapper.toHttpStatus` in `src/main/java/com/devcool/adapters/in/web/util/HttpErrorMapper.java:9`
**Test file:** `src/test/java/com/devcool/adapters/in/web/util/HttpErrorMapperTest.java`
**Practises:** 07 §3 (`@EnumSource`), §9 (exhaustiveness)

**Spec.**
- Every `ErrorCode` (`domain/common/ErrorCode.java`) is raised as a `DomainException` and converted to an HTTP status by `ApiExceptionHandler.handleDomainException`.
- Errors caused by the **client** (bad input, missing resource, conflict, auth failure) must map to a **4xx** status.
- Only a genuine server fault may map to **500**.
- `OK` and `CREATED` are success codes and are never passed in.

**Required cases**
1. An explicit table test (`@CsvSource`) for the five mappings that exist today: `USER_NOT_FOUND`, `EMAIL_ALREADY_USED`, `PASSWORD_WEAK`, `CHANNEL_NOT_FOUND`, `MEMBER_NOT_FOUND`.
2. An **exhaustiveness** test: for every `ErrorCode` except `INTERNAL_SERVER_ERROR`, `OK` and `CREATED`, the status is a 4xx.

**Done when**
- The exhaustiveness test uses `@EnumSource` with `mode = EXCLUDE`. It must fail automatically when someone adds a new `ErrorCode` and forgets to map it.
- The failure output names **which** codes are wrong (parameterized names do that for you)

**Stretch:** for each code that fails, write down the status you think is correct (400? 401? 403? 409? 413? 415? 422?). That's the spec you'd hand to whoever fixes the mapper.

<details><summary>Hints</summary>

- `assertThat(status.is4xxClientError()).isTrue()` works, but the failure message is poor. Try `assertThat(status.value()).as("status for %s", code).isBetween(400, 499)`.
- `@CsvSource({"USER_NOT_FOUND, NOT_FOUND", ...})`: JUnit converts strings to enums implicitly, so the parameters can be `(ErrorCode code, HttpStatus expected)`.
</details>

---

### Exercise 3: In-memory connection registry 🐞

**Target:** `InMemoryConnectionRegistryAdapter` in `src/main/java/com/devcool/adapters/out/realtime/immemory/InMemoryConnectionRegistryAdapter.java`
**Test file:** `src/test/java/com/devcool/adapters/out/realtime/immemory/InMemoryConnectionRegistryAdapterTest.java`
**Practises:** 07 §1 (classic style: test through state, no mocks), §4 (collection assertions), §2 (`@Nested`)

**Spec.** The contract comes from `ConnectionRegistryPort`.
- `registerConnection(conn, user)`, then `getUserId(conn)` returns `user`. After `removeConnection(conn)` it returns `null`.
- `subscribe(conn, channel)` adds the connection to the channel. Many connections can share a channel, and one connection can subscribe to many channels.
- Subscribing twice is idempotent.
- `unsubscribe(conn, channel)` removes **only** that connection from **only** that channel.
- `unsubscribe` of a connection or channel that was never subscribed is a **no-op**. It must not throw.
- `getConnectionsByChannel(channel)` returns the current set. For a channel nobody subscribed to, it returns an **empty set**, never `null`, because callers iterate it directly (`WsSendMessageService.java:43`).

**Required cases:** one test per bullet above (at least 8 tests), grouped with `@Nested` (e.g. `Registration`, `Subscription`, `Unsubscription`).

**Done when**
- There are no mocks at all. You create `new InMemoryConnectionRegistryAdapter()` in `@BeforeEach`.
- Set assertions use `containsExactlyInAnyOrder` / `isEmpty`, and never rely on iteration order
- The "never subscribed" cases use `assertThatCode(...).doesNotThrowAnyException()` (or assert the return value)

**Stretch:** after `removeConnection("c1")`, is `"c1"` still in its channels' sets? Should it be? Write the test for the answer you believe is correct, and explain what goes wrong at runtime if it isn't.

<details><summary>Hints</summary>

- Unsubscribe from a channel that was *never* subscribed, and separately from a channel whose last connection was *already* removed. The two cases take different paths.
</details>

---

## Level 2: Medium (Mockito + application services)

### Exercise 4: Forum channel validation 🐞

**Target:** `ForumCreationStrategy` in `src/main/java/com/devcool/application/service/channel/strategy/ForumCreationStrategy.java`
**Test file:** `src/test/java/com/devcool/application/service/channel/strategy/ForumCreationStrategyTest.java`
**Practises:** 07 §3 (`@MethodSource`), §5 (`verifyNoInteractions`), §9 (one rule per test, boundaries)

**Spec** (from the validation messages in the class):
- The channel type must be `FORUM`
- There are **1 to 10** members (the `memberIds` size)
- A leader is **required**
- The expiry rule: read the condition at line 65 and the message on line 67. **They disagree.** Decide which one is the spec, write your decision in a comment, and test that.
- An invalid command throws `InvalidChannelConfigException` and **nothing is loaded or saved**
- A valid command loads the users, saves the channel, and returns the id from `channelPort.save`

**Required cases**
- `@ParameterizedTest @MethodSource` with one row per invalid configuration: wrong type, 0 members, 11 members, no leader, and your expiry rule. **Each row breaks exactly one rule.**
- Valid boundaries: 1 member and 10 members are both accepted
- A valid command returns the id that `channelPort.save` returned
- The leader id doesn't exist → `UserNotFoundException`, and `save` is never called

**Done when**
- There's a `validForum...` helper, and each invalid row is "valid, except X"
- Every reject asserts `verifyNoInteractions(userPort, channelPort)`
- The exception assertion checks the message fragment too, so that a *different* rule failing can't make the test pass

**Stretch:** capture the saved `Channel`. Is the creator in `getMembers()`? Is the leader? Does `getTotalOfMembers()` equal `getMembers().size()`? What *should* it be? (This connects to exercise 6.)

<details><summary>Hints</summary>

- The mocks are `LoadUserPort` and `ChannelPort`. `@InjectMocks` works here: there's one constructor, and all its types are distinct.
- `userPort.loadByIds(List)` is used for members and `userPort.loadById(Integer)` for the creator and the leader. The happy path needs both stubbed.
- Use `IntStream.rangeClosed(1, 11).boxed().toList()` to build the 11-member list.
</details>

---

### Exercise 5: Lounge channel creation

**Target:** `LoungeCreationStrategy.createChannel` in `src/main/java/com/devcool/application/service/channel/strategy/LoungeCreationStrategy.java:37`
**Test file:** `src/test/java/com/devcool/application/service/channel/strategy/LoungeCreationStrategyTest.java`
**Practises:** 07 §5 (`ArgumentCaptor`), §4 (`extracting`, exception details)

**Spec**
- Type `LOUNGE`, 1–10 members, and **no** leader
- Duplicate member ids → `UserDuplicateException`, **before** any user is loaded
- Member ids that don't exist → `UserNotFoundException`, whose `details["userIds"]` lists **exactly the missing ids**
- On success, the saved channel has:
  - every requested user as a `MEMBER`
  - the creator as the single `CREATOR`
  - `leader == null`
  - the name, boundary type and channel type copied from the command
  - `totalOfMembers` equal to the number of members

**Required cases**
- Each validation rule (reuse the approach from exercise 4)
- Duplicates `[2, 3, 2]` → `UserDuplicateException`, and `verifyNoInteractions(userPort)`
- Ids `[2, 3, 4]` where only 2 and 4 exist → the details contain exactly `[3]`
- Two ids missing → the details contain both, **in any order** (why can't you assume the order? Look at the type of `distinctMemberIds`.)
- Happy path with `ArgumentCaptor<Channel>` covering every bullet in the success spec

**Done when**
- The happy path asserts the members with `extracting(m -> m.getUser().getId(), Member::getRole)` + `containsExactlyInAnyOrder(tuple(...), ...)`
- The missing-ids assertion reads `ex.getDetails()`, not just the exception type

**Stretch:** what happens if the creator's id is also in `memberIds`? Is that allowed by the spec? Write the test that shows the current behaviour.

---

### Exercise 6: Private chat creation 🐞

**Target:** `PrivateChatCreationStrategy` in `src/main/java/com/devcool/application/service/channel/strategy/PrivateChatCreationStrategy.java`
**Test file:** `src/test/java/com/devcool/application/service/channel/strategy/PrivateChatCreationStrategyTest.java`
**Practises:** 07 §5 (captor), §9 ("what would break this?")

**Spec.** A private chat is a conversation between **the creator and exactly one other user**.
- Type `PRIVATE_CHAT`, boundary `PRIVATE`, no leader, no expiry
- `memberIds` holds exactly one id (the *other* participant)
- The saved channel's members are **both participants**: the other user as `MEMBER` and the creator as `CREATOR`. Its `totalOfMembers` equals the number of members.

**Required cases**
- One test per validation rule (5 rules; 0 members and 2 members both count for the size rule)
- Happy path: capture the channel and assert **both** participants and their roles are present
- Happy path: `totalOfMembers == members.size()`

**Done when**
- The captor assertions check user ids **and** roles, not just the size
- If a test goes red, your comment explains the user-visible consequence (what can the creator *not* do afterwards? Think about the membership checks in `MessageService.getMessages`.)

---

### Exercise 7: User registration and password change 🐞

**Target:** `UserService` in `src/main/java/com/devcool/application/service/UserService.java`
**Test file:** `src/test/java/com/devcool/application/service/UserServiceTest.java`
**Practises:** 07 §5 (verify commands, `never()`, captor), §2 (`@Nested`)

**Spec: `register`**
- Username already taken → `UsernameAlreadyUsedException`. Nothing is hashed or saved.
- Email already taken → `EmailAlreadyUsedException`. Nothing is hashed or saved.
- Otherwise it saves a user with:
  - the **hashed** password (never the raw one)
  - `role = USER`, `status = ACTIVE`, `tokenVersion = 1`
  - username, email and name copied from the command
- It returns the id from `userPort.save`

**Spec: `change(id, current, new)`**
- Unknown user → `UserNotFoundException`
- Wrong current password → returns `false`. The new password is **not** hashed and **not** stored.
- Correct current password → stores `hash(new)` for that user id and returns the port's result

**Spec: `byEmail(email)`**
- Returns the user with that email if one exists, else empty

**Required cases:** every bullet above. Group them with `@Nested class Register`, `@Nested class Change` and `@Nested class ByEmail`.

**Done when**
- The captured `User` is asserted with `SoftAssertions` or `usingRecursiveComparison().ignoringFields(...)`
- `hasher.hash` is stubbed with a distinctive value (e.g. `"HASHED(secret)"`), and the test proves *that* value was stored and the raw password never was
- The wrong-password case verifies `never()` on **both** `hasher.hash` and `userPort.updatePassword`

<details><summary>Hints</summary>

- `RegisterUserCommand` is in `domain/user/port/in/command/`. Check its record components.
- For `byEmail`, the spec says what should happen. Stub the port so that a user *does* exist.
</details>

---

### Exercise 8: Message listing and dispatch

**Target:** `MessageService` in `src/main/java/com/devcool/application/service/chat/MessageService.java`
**Test file:** `src/test/java/com/devcool/application/service/chat/MessageServiceTest.java`
**Practises:** 07 §5 (manual construction, exact-arg stubbing), §9 (boundaries, order of checks)

**Spec: `getMessages(command)`**
- The channel doesn't exist → `ChannelNotFoundException`, and membership is **not** checked
- The caller isn't a member → `MemberNotFoundException`, and messages are **not** fetched
- The service fetches `limit + 1` rows starting from `cursorId`, to learn whether another page exists
- The result:

| Rows returned by port | `items` | `hasMore` | `cursorId` |
|---|---|---|---|
| 0 | empty | false | null |
| fewer than `limit` | all | false | id of last item |
| exactly `limit` | all | false | id of last item |
| `limit + 1` | first `limit` | true | id of the `limit`-th item (**not** the extra one) |

**Spec: `save(command)`**
- Dispatches to the strategy registered for `command.contentType()` and returns its result
- No strategy for that type → `InvalidMessageConfigException`, and no strategy is invoked

**Required cases:** each row of the table, both guard clauses, and both `save` paths.

**Done when**
- The service is built by hand in `@BeforeEach`: `new MessageService(List.of(textStrategy, imageStrategy), messagePort, channelPort, memberPort)`. `@InjectMocks` can't do this (07 §5).
- The fetch is stubbed with **exact** arguments, e.g. `findMessages(5, 100, 21)` for `limit=20`. That makes the `limit + 1` a proven behaviour, not an assumption.
- The guard tests use `verify(..., never())` / `verifyNoInteractions` to prove the short-circuit
- A small helper builds `MessageItem`s with given ids, e.g. `items(101, 100, 99)`

<details><summary>Hints</summary>

- The strategies are mocks of `MessageCreationStrategy`. Their `getSupportedType()` must be stubbed **before** the constructor runs, because the constructor calls it.
- If Mockito reports `UnnecessaryStubbingException` for `getSupportedType` in some `@Nested` class, ask yourself which tests really need the service built with strategies.
</details>

---

## Level 3: Hard (collaboration, ordering, slices, design)

### Exercise 9: Message creation strategies (contract test) 🐞

**Targets:**
- `AbstractMessageCreationStrategy.createMessage` in `src/main/java/com/devcool/application/service/chat/strategy/AbstractMessageCreationStrategy.java:27`
- `TextMessageCreationStrategy`
- `ImageMessageCreationStrategy`

**Test files:** `src/test/java/com/devcool/application/service/chat/strategy/`, laid out as:
- `MessageCreationStrategyContractTest.java` (abstract)
- `TextMessageCreationStrategyTest.java`
- `ImageMessageCreationStrategyTest.java`

**Practises:** the abstract contract-test pattern, 07 §9 (order of checks), §5 (captor), §4 (time assertions)

**Spec: shared by every strategy (the contract)**
- The channel doesn't exist → `ChannelNotFoundException`. Membership is never queried, and nothing is saved.
- The sender isn't a member → `MemberNotFoundException`, and nothing is saved
- Otherwise it saves one `Message` with `senderId = userId`, `channelId`, the command's `contentType`, and `createdTime` set to "now". It returns the id from `messagePort.save`.

**Spec: text**
- `content` is the command's content, and `media` is null

**Spec: image**
- `media.path` is the command's content (an S3 key from the upload endpoint), `media.createdTime` is "now", and the message `content` is null

**Spec: coverage**
- Every `ContentType` a user can send has a strategy. The upload endpoint accepts JPEG, PNG, WebP **and MP4** (CLAUDE.md).

**Required**
1. `abstract class MessageCreationStrategyContractTest` holds the `@Mock` ports, an `abstract MessageCreationStrategy createStrategy(ChannelPort, MemberPort, MessagePort)`, and the three contract tests. The two concrete subclasses inherit them and add their type-specific happy-path assertions.
2. The time assertions use `isBetween(before, after)`.
3. An **exhaustiveness** test: `@EnumSource(ContentType.class)`. Build a `MessageService` with the **real** Text and Media strategies (with mocked ports), and assert that `save` does not throw `InvalidMessageConfigException` for any type.

**Done when**
- The contract tests appear in the report under **both** subclasses
- The ordering test sets up *both* failures (channel missing **and** not a member) and still expects `ChannelNotFoundException`
- There's no `any()` in the captor-based assertions; the saved message is checked field by field

**Stretch:** `createMessage` is `final` and `buildMessage` is `protected`. Why is that a good design for testability *and* for safety? One paragraph.

---

### Exercise 10: WebSocket send and broadcast 🐞

**Target:** `WsSendMessageService.sendMessage` in `src/main/java/com/devcool/application/service/chat/WsSendMessageService.java:26`
**Test file:** `src/test/java/com/devcool/application/service/chat/WsSendMessageServiceTest.java`
**Practises:** 07 §5 (`InOrder`, `doThrow`), §6 (**mock vs fake**)

**Spec**
- The message is **persisted before** anyone receives it
- If persisting fails, the exception propagates and **nobody** receives anything
- Every connection subscribed to the channel receives an `OutboundWsEvent("MESSAGE", channelId, userId, contentType.name(), content)`, **except the sender's own connection**
- A channel with no subscribers → the message is saved and nothing is sent, **without error**

**Required: part A (mocked registry)**
- The save is called with a `CreateMessageCommand` carrying the right fields (use a captor)
- Three connections `c1` (sender), `c2`, `c3` → `c2` and `c3` each receive the exact event, and `c1` receives nothing
- `inOrder(saveUseCase, emitter)` proves save-then-send
- Save throws → `verifyNoInteractions(emitter)`
- No subscribers → no send, no exception

**Required: part B (real registry as a fake)**
- Rewrite the "no subscribers" and "three connections" cases with a **real** `InMemoryConnectionRegistryAdapter` instead of a mock. Subscribe connections for real.

**Done when**
- The event is asserted with `eq(new OutboundWsEvent(...))` or a captor. It's a record, so `equals` is structural.
- Your review request answers: **why does part A pass and part B fail on the same spec?** (07 §5 "unstubbed methods return defaults" and §6.)

**Stretch:** what should happen if sending to `c2` throws? Should `c3` still get the message? Look at `WsRealtimeEmitterAdapter.java:23-33` to see what the real adapter does. Then decide whether the *service* should rely on that. Write the test for your decision.

---

### Exercise 11: `MessageController` web slice

**Target:** `MessageController.getMessages` in `src/main/java/com/devcool/adapters/in/web/controller/MessageController.java:30`
**Test file:** `src/test/java/com/devcool/adapters/in/web/controller/MessageControllerTest.java`
**Practises:** 07 §8 (`@WebMvcTest`, `@MockitoBean`, `spring-security-test`, `jsonPath`)

**Spec** (the HTTP contract for `GET /api/v1/channels/{channelId}/messages?cursorId=&limit=`)
- Authenticated → 200. The body is `ApiSuccessResponse` with `code = "SVR_200"` and a `data` object holding `items[]`, `cursorId` and `hasMore`.
- The query receives `userId` from the **authenticated principal**, `channelId` from the path, and `cursorId`/`limit` from the query string. `limit` defaults to **20** when omitted.
- `limit` outside `1..100` → 422 with `code = "VLD_401"`, and the use case is **not** called
- The use case throws `MemberNotFoundException` → the status comes from `HttpErrorMapper`, and the body `code = "MEMBER_404"`
- The use case throws `ChannelNotFoundException` → 404, with `code = "CHANNEL_401"`
- Unauthenticated → rejected, and the use case is not called

**Required cases:** each bullet. Also the boundaries `limit=1` and `limit=100` (accepted) and `0` and `101` (rejected).

**Setup puzzle (part of the exercise).** Get the slice to start and route requests to the controller. You'll need to work out:
1. Which beans the slice doesn't create that `MessageController` needs
2. Which beans `JwtAuthFilter` needs. The slice *does* include it, because it's a `Filter` `@Component`.
3. Whether to `@Import(SecurityConfig.class)`, and how the unauthenticated status changes when you do. Explain the difference in your review request.

**Done when**
- The command passed to the use case is captured and all four fields asserted
- The JSON assertions use `jsonPath`, and at least one checks a nested item field (`$.data.items[0].id`)
- Every rejection verifies `verifyNoInteractions(messageQuery)`

<details><summary>Hints</summary>

- `@MockitoBean` is in `org.springframework.test.context.bean.override.mockito`.
- **Trap:** if you `@MockitoBean JwtAuthFilter` itself, the mocked `doFilter` does nothing. The filter chain stops there, and you get an empty 200 that never reached the controller. Mock the filter's *dependencies* instead, so the real filter runs and passes requests without an `Authorization` header straight through.
- `.with(user("7"))`: the principal's `getName()` is `"7"`, which the controller parses as the user id.
</details>

---

### Exercise 12 (bonus, design): Login service testability

**Target:** `AuthenticateUserService.login` in `src/main/java/com/devcool/application/service/AuthenticateUserService.java:32`
**Test file:** `src/test/java/com/devcool/application/service/AuthenticateUserServiceTest.java`
**Practises:** 07 §11 (testability smells), §5 (`mockStatic`)

**Spec**
- Unknown username → `UserNotFoundException`, and no tokens are issued
- Wrong password → `PasswordIncorrectException`. No tokens are issued, and the login time is not updated.
- Success → the login time is updated and saved, a token pair is issued, old refresh tokens are deleted, the new refresh token is stored, and the pair is returned. `deleteOldRefreshTokens` runs **before** `store`.

**Tasks**
1. Write the two failure-path tests. They should be easy.
2. Try to write the success-path test with only `@Mock` ports. Record what breaks, and why (look at line 46).
3. Make it pass with `try (MockedStatic<JwtUtils> jwt = mockStatic(JwtUtils.class)) { ... }`.
4. In a short note (a comment block at the top of the test class is fine), answer:
   - What does the test now know that it *shouldn't* have to know?
   - Which outbound port would you introduce, or which existing port would you extend, so that step 3 is unnecessary? Sketch its signature.
   - The wrong-password test: what does `PasswordIncorrectException` put into `getDetails()`? Is that acceptable? (See the README's "Fix these first" table.)

**Done when:** I can read your note and agree or disagree with a concrete design proposal.

---

## Progress

Fill this in as you go.

| # | Exercise | Level | 🐞 | Status | Reviewed |
|---|---|---|---|---|---|
| 1 | Token version check | Easy | ✓ | ☐ | ☐ |
| 2 | Error code → HTTP status | Easy | ✓ | ☐ | ☐ |
| 3 | In-memory connection registry | Easy | ✓ | ☐ | ☐ |
| 4 | Forum validation | Medium | ✓ | ☐ | ☐ |
| 5 | Lounge creation | Medium | | ☐ | ☐ |
| 6 | Private chat creation | Medium | ✓ | ☐ | ☐ |
| 7 | UserService | Medium | ✓ | ☐ | ☐ |
| 8 | MessageService | Medium | | ☐ | ☐ |
| 9 | Message strategies (contract) | Hard | ✓ | ☐ | ☐ |
| 10 | WS send and broadcast | Hard | ✓ | ☐ | ☐ |
| 11 | MessageController slice | Hard | | ☐ | ☐ |
| 12 | Login testability (bonus) | Hard | | ☐ | ☐ |

**Suggested path:** 1 → 2 → 3 → 4 → 8 → 10 → 11. That's one per technique. Do the rest for repetition.

**Note:** exercises 8 and 9 target the message-creation code as it is on `refactor/message-creation-improvements`, including the uncommitted working-tree changes. If that code changes, tell me and I'll update the specs.
