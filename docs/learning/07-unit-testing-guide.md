# 07 — Unit Testing: Theory, Tools, Craft

The question this chapter answers: **what does a *good* unit test look like, and how do you build one with JUnit 5, Mockito, AssertJ and Spring's test slices?**

Chapter 06 explained *why* bugs survived. This chapter is the *how*. Chapter 08 is where you practise it.

You already write decent tests. `MediaServiceTest` uses `ArgumentCaptor`, `verifyNoInteractions` and real boundary cases. So this chapter skips "what is `@Test`" and puts its effort into **choosing what to test, and making each test prove something**.

Everything below uses the versions in `pom.xml`: Spring Boot 3.5.6 (Spring Framework 6.2), JUnit 5 (Jupiter), Mockito 5, AssertJ, and `spring-security-test`. All of them come in through `spring-boot-starter-test`. You don't need to add anything.

---

## 1. What a unit test is

### The unit is a behaviour, not a class

People often read "unit" as "one class, with every collaborator mocked". A more useful definition:

> A unit test checks **one behaviour**. It runs **in isolation from other tests** and **fast enough to run on every save**.

"In isolation" means *tests don't affect each other*. It does not mean *every collaborator must be a mock*. Two schools:

| School | Replaces with doubles… | Strength | Weakness |
|---|---|---|---|
| **Classic (Detroit)** | Only what's slow, non-deterministic or external (DB, S3, clock, network) | Tests survive refactoring; they catch integration bugs between your own classes | A failure can point at several classes |
| **Mockist (London)** | Every collaborator | Pinpoints the failing class; forces you to design interfaces | Tests couple to *how* the code works; refactors break tests that still describe correct behaviour |

Hexagonal architecture gives you a natural middle ground. **Mock the ports and use everything else for real.** The ports are the seams the architecture already declares as "the outside world".

Each strength and weakness is demonstrated on this repo's own code (a real refactor, a real bug) in [09 — Classic vs Mockist, Shown on Real Code](09-test-schools.md).

### The pyramid

```
        ▲  few      E2E / @SpringBootTest + real DB      slow, broad, brittle
       ▲▲▲          Slice tests (@WebMvcTest, @DataJpaTest)
     ▲▲▲▲▲▲▲  many  Unit tests (JUnit + Mockito)          ms each, precise
```

This chapter covers the bottom layer plus `@WebMvcTest`.

### FIRST

- **Fast**: milliseconds. No Spring context, no Docker.
- **Independent**: any order, any subset. No shared mutable state.
- **Repeatable**: same result every run. No `Instant.now()` comparisons by equality, no randomness, no network.
- **Self-validating**: passes or fails on its own. No reading logs to decide.
- **Timely**: written with the code (or before it), not months later.

**The rule: a unit test pins one behaviour, runs in milliseconds, and replaces only what's outside your process.**

---

## 2. Anatomy of a test

### Arrange / Act / Assert

`MediaServiceTest.upload_withEmptyFile_throwsInvalidMediaContentException` has the classic shape:

```java
@Test
void upload_withEmptyFile_throwsInvalidMediaContentException() {
  // Arrange
  MockMultipartFile empty = new MockMultipartFile("file", "empty.jpg", "image/jpeg", new byte[0]);
  UploadMediaCommand command = new UploadMediaCommand(empty, 0, "image/jpeg", USER_ID, CHANNEL_ID);

  // Act + Assert (exceptions merge the two)
  assertThatThrownBy(() -> mediaService.upload(command))
      .isInstanceOf(InvalidMediaContentException.class);
  verifyNoInteractions(storagePort);
}
```

Guidelines:
- **One Act per test.** If you call the method under test twice, you're testing two behaviours.
- **Arrange should read like the spec.** Hide noise in helpers (`makeCommand(...)`, `aUser()`), but keep the *values that matter to this case* visible in the test body.
- **Assert on outcomes first, interactions second.** Return values and captured arguments tell you more than `verify(x).called()`.

### Naming

This repo already uses `method_condition_expectedResult`:

```
upload_withUnsupportedContentType_throwsUnsupportedMediaTypeException
```

Keep it. A reader should know what broke from the test name alone, without opening the file. If names get long, group them with `@Nested` (§3) so the method name holds only the condition and the outcome.

### One behaviour per test, not one assert per test

This is fine: several asserts that together describe **one** outcome.

```java
assertThat(captured.objectKey()).isEqualTo(key);
assertThat(captured.contentType()).isEqualTo("video/mp4");
assertThat(captured.contentLength()).isEqualTo(file.getSize());
```

This is not: one test that checks the happy path, then the empty-file path, then the bad-type path. When it fails, you only learn about the first problem.

**The rule: a test name plus one failure message should tell you what behaviour is broken.**

---

## 3. JUnit 5 (Jupiter) features you'll actually use

### Lifecycle

```java
@ExtendWith(MockitoExtension.class)
class ChannelServiceTest {
  @BeforeEach void setUp() { ... }   // runs before EACH test, on a NEW instance
  @AfterEach  void tearDown() { ... }
  @BeforeAll static void once() { ... }  // static; for expensive, immutable setup only
}
```

JUnit creates a **new test class instance per test method**. Instance fields are fresh every time. Don't use `static` mutable fields to "share" state: that breaks *Independent*.

### `@Nested` + `@DisplayName`

Group tests by method or scenario:

```java
class MessageServiceTest {
  @Nested
  class GetMessages {
    @Test void whenChannelMissing_throwsChannelNotFound() { ... }
    @Test void whenMoreRowsThanLimit_setsHasMoreAndTrims() { ... }
  }

  @Nested
  class Save {
    @Test void withUnregisteredContentType_throwsInvalidMessageConfig() { ... }
  }
}
```

Nested classes can have their own `@BeforeEach`. It runs *after* the outer one.

### Parameterized tests: the most under-used tool

When the *same behaviour* should hold for *many inputs*, write one test and feed it the inputs.

```java
// Simple values
@ParameterizedTest
@ValueSource(strings = {"", "  ", "\t"})
void getPresignedUrl_withBlankKey_throws(String key) { ... }

// Rows of inputs + expected output
@ParameterizedTest(name = "tokenVersion={0}, claim={1} → {2}")
@CsvSource({
  "3, 3, true",
  "3, 2, false",
})
void isTokenVersionValid(int dbVersion, int claimVersion, boolean expected) { ... }

// Every constant of an enum (exhaustiveness!)
@ParameterizedTest
@EnumSource(value = ErrorCode.class, mode = EnumSource.Mode.EXCLUDE, names = {"OK", "CREATED"})
void everyErrorCodeHasAStatus(ErrorCode code) { ... }

// Complex objects
@ParameterizedTest(name = "{0}")
@MethodSource("invalidForumCommands")
void createChannel_rejectsInvalidConfig(String description, CreateChannelCommand command) { ... }

static Stream<Arguments> invalidForumCommands() {
  return Stream.of(
      Arguments.of("zero members", forumWithMembers(List.of())),
      Arguments.of("eleven members", forumWithMembers(IntStream.rangeClosed(1, 11).boxed().toList())));
}
```

Also available: `@NullSource`, `@EmptySource`, `@NullAndEmptySource` (combine them with `@ValueSource`) and `@CsvFileSource`.

`@EnumSource` deserves special attention. It gives you **exhaustiveness tests**: when someone adds a new enum constant, a test fails automatically if the new constant isn't handled. Chapter 06 lists several live bugs this would have caught.

### Other JUnit bits

- `assertAll(...)`: runs every assertion and reports all failures at once. AssertJ's `SoftAssertions` is usually nicer.
- `@Disabled("reason")`: always give the reason. A disabled test with no reason gets forgotten.
- `@Tag("slow")`: lets you filter test runs.
- `@TempDir Path dir`: a real temp directory, cleaned up after the test.

**The rule: if you're copy-pasting a test and changing one value, it should be a `@ParameterizedTest`.**

---

## 4. AssertJ: write assertions that explain themselves

Prefer AssertJ over JUnit's `assertEquals`. The failure messages are far better, and the chains read like the spec.

```java
// Basic
assertThat(result.hasMore()).isTrue();
assertThat(key).startsWith("channel/42/").endsWith(".jpg");

// Collections
assertThat(channel.getMembers()).hasSize(3);
assertThat(channel.getMembers())
    .extracting(Member::getRole)
    .containsExactlyInAnyOrder(MemberType.MEMBER, MemberType.MEMBER, MemberType.CREATOR);
assertThat(channel.getMembers())
    .filteredOn(m -> m.getRole() == MemberType.CREATOR)
    .singleElement()
    .extracting(m -> m.getUser().getId())
    .isEqualTo(CREATOR_ID);

// Exceptions: check the type AND the part of the message/details that matters
assertThatThrownBy(() -> strategy.createChannel(command))
    .isInstanceOf(InvalidChannelConfigException.class)
    .hasMessageContaining("leader");

// Exceptions with typed access
assertThatExceptionOfType(UserNotFoundException.class)
    .isThrownBy(() -> strategy.createChannel(command))
    .satisfies(ex -> assertThat(ex.getDetails()).containsEntry("userIds", List.of(3)));

// No exception
assertThatCode(() -> registry.unsubscribe("c1", 999)).doesNotThrowAnyException();

// Time (never compare Instant.now() with isEqualTo)
Instant before = Instant.now();
Message saved = ...;
Instant after = Instant.now();
assertThat(saved.getCreatedTime()).isBetween(before, after);

// Compare whole objects field-by-field without writing equals()
assertThat(actualUser).usingRecursiveComparison()
    .ignoringFields("password", "lastLoginTime")
    .isEqualTo(expectedUser);

// Soft assertions: report every failure, not just the first
SoftAssertions.assertSoftly(s -> {
  s.assertThat(user.getRole()).isEqualTo(Role.USER);
  s.assertThat(user.getStatus()).isEqualTo(UserStatus.ACTIVE);
  s.assertThat(user.getTokenVersion()).isEqualTo(1);
});
```

**Assertion strength matters.** `assertThat(result).isNotNull()` passes for a *wrong* result too. Ask: *which buggy implementation would still pass this assert?* If the answer is "most of them", strengthen it.

**The rule: assert the specific value the spec demands. `isNotNull()` and `isInstanceOf()` on their own rarely prove anything.**

---

## 5. Mockito

### Setup

```java
@ExtendWith(MockitoExtension.class)     // enables annotations + strict stubs
class UserServiceTest {
  @Mock UserPort userPort;
  @Mock PasswordHasherPort hasher;
  @InjectMocks UserService userService;  // constructor injection with the mocks above
  @Captor ArgumentCaptor<User> userCaptor;
}
```

`@InjectMocks` picks the biggest constructor and fills it **by type**. It can't build a `List<ChannelCreationStrategy>` for `ChannelService` or `MessageService`. For those, construct the object yourself:

```java
@BeforeEach
void setUp() {
  when(textStrategy.getSupportedType()).thenReturn(ContentType.TEXT);
  messageService = new MessageService(List.of(textStrategy), messagePort, channelPort, memberPort);
}
```

That's also a good habit in general. **Explicit construction documents the dependencies and fails at compile time if they change.**

### Stubbing

```java
when(userPort.findById(7)).thenReturn(Optional.of(user));
when(userPort.existsByUsername("bob")).thenReturn(true);
when(channelPort.save(any())).thenReturn(100);
when(hasher.hash("raw")).thenReturn("HASHED");
when(port.load(1)).thenThrow(new IllegalStateException("boom"));

// void methods: the when(...) form can't wrap a void call
doThrow(new RuntimeException("socket closed"))
    .when(emitter).sendMessageToConnection(eq("c2"), any());

// consecutive calls
when(port.next()).thenReturn(1, 2, 3);

// computed answer
when(channelPort.save(any())).thenAnswer(inv -> { Channel c = inv.getArgument(0); return 100; });
```

**Unstubbed methods return defaults**: `null` for objects, `0`/`false` for primitives, **empty** `Optional`/`List`/`Set`/`Map` for those types. Remember the last one. A mock returns an empty `Set`, and the real adapter might return `null`. Your mock can make the port contract look safer than the real implementation is (exercise 10 is about exactly this).

### Argument matchers: all or nothing

```java
when(messagePort.findMessages(1, null, 21)).thenReturn(rows);           // OK: all raw values
when(messagePort.findMessages(eq(1), isNull(), eq(21))).thenReturn(rows); // OK: all matchers
when(messagePort.findMessages(eq(1), null, 21)).thenReturn(rows);       // InvalidUseOfMatchersException
```

Prefer **exact values** over `any()` whenever the value is part of the contract. `findMessages(eq(1), isNull(), eq(21))` *proves* the service fetched `limit + 1`. `findMessages(any(), any(), any())` proves nothing.

### Verifying interactions

```java
verify(userPort).save(any());                    // exactly once
verify(userPort, times(2)).findById(7);
verify(userPort, never()).save(any());           // negative path: the side effect did NOT happen
verifyNoInteractions(channelPort);               // didn't touch this mock at all
verifyNoMoreInteractions(emitter);               // nothing beyond what you verified

InOrder inOrder = inOrder(saveUseCase, emitter); // order matters (e.g. persist before broadcast)
inOrder.verify(saveUseCase).save(any());
inOrder.verify(emitter).sendMessageToConnection(eq("c2"), any());
```

When to verify:
- **Commands** (side effects: save, send, delete): verify them. That's the observable behaviour.
- **Queries** (stubbed lookups): usually *don't* verify them. The stub already has to be used for the result to be right, so verifying it again only couples the test to the implementation.
- **Negative paths**: `never()` / `verifyNoInteractions` are essential. "Rejected the input" is only half the behaviour. "…and wrote nothing" is the other half.
- Use `verifyNoMoreInteractions` sparingly. It makes tests brittle to harmless additions.

### ArgumentCaptor: assert what was *sent* to a port

```java
verify(channelPort).save(channelCaptor.capture());
Channel saved = channelCaptor.getValue();
assertThat(saved.getLeader()).isNull();
assertThat(saved.getMembers()).extracting(Member::getRole).contains(MemberType.CREATOR);
```

This is the most important Mockito tool for application services in this repo. Most service methods *build a domain object and hand it to a port*. The captor is how you inspect that object.

### Strict stubs

`MockitoExtension` uses `Strictness.STRICT_STUBS` by default:
- Stubbing that the test never uses → `UnnecessaryStubbingException`. This is a feature: it means your Arrange has dead code, or the code took a path you didn't expect.
- Stubbed with args A, called with args B → `PotentialStubbingProblem`.

Don't reach for `lenient()` as a first response. Remove the unused stub. If a stub in `@BeforeEach` is genuinely only used by some tests, move it into those tests or into a `@Nested` class.

### BDD style (optional)

```java
given(userPort.existsByUsername("bob")).willReturn(true);   // Arrange
...
then(userPort).should(never()).save(any());                // Assert
```

Same engine as `when`/`verify`, but it reads better against Given/When/Then. Pick one style per file.

### `@Spy` and `mockStatic`

- `@Spy` wraps a real object so you can stub part of it. If you need it on the class under test, the class is usually doing too much.
- `mockStatic(JwtUtils.class)` works (Mockito 5 uses the inline mock maker by default), but **needing it is a design smell** (§11). It must go in a try-with-resources block, or it leaks into other tests.

**The rule: stub queries, verify commands, capture what goes into ports, and always assert that negative paths had no side effects.**

---

## 6. Test doubles: pick the right one

| Double | What it is | Example here |
|---|---|---|
| **Dummy** | Passed but never used | A `User` built only to satisfy a constructor |
| **Stub** | Returns canned answers | `when(userPort.findById(7)).thenReturn(Optional.of(u))` |
| **Spy** | Records calls so you can check them | `verify(channelPort).save(captor.capture())` |
| **Mock** | Stub + spy, with expectations | Mockito `@Mock` does both |
| **Fake** | A real, simplified working implementation | `InMemoryConnectionRegistryAdapter`: a genuine in-memory registry |

**Fakes are underrated.** For `WsSendMessageService`, a real `InMemoryConnectionRegistryAdapter` is a better collaborator than a mocked `ConnectionRegistryPort`:
- You `subscribe(...)` for real, instead of stubbing `getConnectionsByChannel` to return whatever you *think* it returns.
- It behaves like production, including production's bugs (a mock returns `Set.of()` where the real one returns `null`).

Mock when the collaborator is external, slow, or when you need to *verify a command was sent* (`RealtimeEmitterPort`). Use a fake when an in-memory implementation exists and state is what matters.

**Don't mock value objects.** Build `CreateChannelCommand`, `User`, `Message` and `Media` for real. They're records or Lombok builders, and mocking them only hides bugs.

**The rule: mock at the boundary, and use real objects (or fakes) for everything else.**

---

## 7. Testing a hexagonal app, layer by layer

| Layer | What to test | Doubles | Example here |
|---|---|---|---|
| `domain/*/model` | Invariants, behaviour methods | **None** | `User.isTokenVersionValid` |
| `domain/*/policy` + `application/service` | Business rules, orchestration, what's sent to ports | **Mock outbound ports** | `ForumCreationStrategy`, `MessageService` |
| `adapters/out/*` | Translation to/from the SDK/JPA | **Mock the SDK** (or `@DataJpaTest`) | `S3StorageAdapterTest` |
| `adapters/in/web` | HTTP contract: status, JSON, validation, auth, error mapping | **`@WebMvcTest` + mocked inbound ports** | `MessageController` |
| Pure utilities in adapters | Mapping tables, parsing | **None** | `HttpErrorMapper` |

Two consequences of the architecture:

1. **Application services should be testable without Spring.** If `new FooService(mockA, mockB)` isn't enough to test a service, there's a boundary violation. Look for a static call into an adapter (§11).
2. **Mock ports you own, not types you don't own.** `MessagePort` is yours: its contract is whatever your domain says it is. Don't mock `JpaRepository` inside a service test. The service shouldn't know it exists (CLAUDE.md, "Application service rules").

**The rule: each layer is tested at its own boundary, and the ports are where the mocks go.**

---

## 8. Spring Boot test support (web slice)

### What `spring-boot-starter-test` gives you

JUnit Jupiter, Mockito (+ `mockito-junit-jupiter`), AssertJ, Hamcrest, JSONassert, JsonPath, Spring Test (`MockMvc`), and Spring Boot Test (`@WebMvcTest`, `@SpringBootTest`...). `spring-security-test` is a separate dependency, and it's already in `pom.xml`.

### `@SpringBootTest` is not a unit test

It boots the *whole* application context: every bean, the datasource, S3 config, JWT secrets. That's slow, and in this repo it currently can't start at all without a datasource and `JWT_*` env vars (chapter 06 §1). Use it for a handful of wiring tests, never for business rules.

### `@WebMvcTest`: test one controller's HTTP contract

`@WebMvcTest(MessageController.class)` builds a **slice**: the Spring MVC infrastructure plus a filtered set of beans. What's in:
- the named controller
- `@ControllerAdvice` / `@RestControllerAdvice` → `ApiExceptionHandler` is included
- `Filter` beans → **`JwtAuthFilter` is included**, because it's a `@Component` extending `OncePerRequestFilter`
- `WebMvcConfigurer`, `Converter`, `@JsonComponent`, Jackson
- Spring Security auto-configuration

What's **not** included:
- `@Service`, `@Component` (non-web), `@Repository` → the inbound port and `MessageDtoMapper` must be supplied
- plain `@Configuration` classes → **`SecurityConfig` is not picked up** unless you `@Import` it. Without it you get Spring Boot's *default* security (everything authenticated, CSRF on, HTTP Basic).

Supplying beans (Spring Framework 6.2 / Boot 3.4+):

```java
import org.springframework.test.context.bean.override.mockito.MockitoBean;

@WebMvcTest(MessageController.class)
@Import({MessageDtoMapper.class /*, SecurityConfig.class */})
class MessageControllerTest {
  @Autowired MockMvc mockMvc;
  @MockitoBean GetMessageQuery messageQuery;   // replaces @MockBean, which is deprecated since Boot 3.4
  // JwtAuthFilter's own dependencies also need beans. Which ones? (exercise 11)
}
```

A typical request:

```java
mockMvc.perform(get("/api/v1/channels/{id}/messages", 5)
        .param("limit", "10")
        .with(user("7")))                       // spring-security-test: authenticated principal named "7"
    .andExpect(status().isOk())
    .andExpect(jsonPath("$.data.hasMore").value(false))
    .andExpect(jsonPath("$.data.items[0].id").value(101));
```

Security helpers from `spring-security-test`:
- `@WithMockUser(username = "7", authorities = "USER")` on the method or class
- `.with(user("7").authorities(...))` per request
- `.with(csrf())`: needed for POST/PUT/DELETE when CSRF is on (default security). This repo's `SecurityConfig` disables CSRF.

`MessageController` reads the user with `Integer.valueOf(auth.getName())`, so the mock username must be numeric.

**The rule: `@WebMvcTest` tests the HTTP contract (status codes, JSON shape, validation, auth and error mapping), with the use case mocked. Business rules belong in the service test.**

---

## 9. Designing test cases: what to test

Coverage percentage tells you which lines ran. It doesn't tell you whether the behaviour is right. These techniques find the cases that matter.

### Equivalence partitions

Split the input space into groups that *should* behave the same, and test one representative per group. For forum `memberIds.size()`: `{0}` invalid, `{1..10}` valid, `{11+}` invalid.

### Boundary values

Bugs cluster at edges. For each boundary, test **both sides**: 0, 1, 10, 11. `<` vs `<=` mistakes only show up at the edge. For pagination: `fetched.size()` = `limit - 1`, `limit`, `limit + 1`, and 0.

### Every branch of every validation

Each `if (...) throw` is a rule. Write one test per rule, and **make only that rule fail**. Start from a valid command and break exactly one field. If your "invalid leader" test also has 0 members, it passes because of the member rule, and the leader rule stays untested.

`CreateChannelCommand` is a record, so it has no builder. A small factory with the "interesting" fields as parameters works well:

```java
private static CreateChannelCommand lounge(Integer leaderId, List<Integer> memberIds) {
  return new CreateChannelCommand(
      "general", BoundaryType.PUBLIC, null, ChannelType.LOUNGE, CREATOR_ID, leaderId, memberIds);
}

@Test void createChannel_withLeader_throwsInvalidChannelConfig() {
  var command = lounge(99, List.of(2, 3));   // only the leader is wrong
  ...
}
```

### Order of checks and short-circuiting

When a method validates A, then B, then does work, test that:
- a failure in A throws **A's** exception, even when B would also fail. The order is part of the contract: see the Javadoc on `AbstractMessageCreationStrategy.createMessage` (`AbstractMessageCreationStrategy.java:20-32`)
- after a failure, **later side effects don't happen** (`never()`)

### Exhaustiveness

Any `switch` or `Map<Enum, X>` is a promise that every enum constant is handled. `@EnumSource` turns that promise into a test that fails automatically when a constant is added.

### Ask "what would break this?"

For each test, name the mutation it kills: "if someone changes `>=` to `>`, this goes red". A test that no plausible bug can break isn't doing any work. (Tools like PIT mutation testing automate this question. It's worth trying once you're comfortable.)

### Spec first, code second

Write the expected value from **the requirement**, not by reading the implementation and copying what it does. If you derive expectations from the code, the test just locks in whatever the code does, bugs included. Several exercises in chapter 08 contain real bugs. The only way to find them is to write down what *should* happen before you run the test.

**The rule: partitions, boundaries, one-rule-per-test, order, exhaustiveness. And the expected value comes from the spec, never from the implementation.**

---

## 10. Anti-patterns

| Anti-pattern | Why it hurts | Instead |
|---|---|---|
| **Asserts nothing** (`DevCoolApplicationTests`) | Green forever, proves nothing, and takes the place of a real test | Every test ends in an assertion or `verify` |
| **Mirrors the implementation** (re-computes the expected value with the same formula) | Bug in the formula = bug in the test | Hard-code expected values from the spec |
| **Over-mocking** (mocking `User`, `Channel`, records) | Tests pass against objects that don't behave like the real ones | Build value objects for real |
| **`verify` on every stubbed query** | Refactoring the lookup breaks tests even though the behaviour didn't change | Verify commands, not queries |
| **`Instant.now()` equality** | Flaky: fails when the clock ticks between two calls | `isBetween(before, after)`, or inject a `Clock` |
| **Logic in tests** (`if`, loops computing expectations) | Tests need tests | Parameterize instead |
| **Mega-test** covering 5 behaviours | First failure hides the rest; name can't describe it | One behaviour per test |
| **Shared mutable static state** | Order-dependent failures | Fresh state in `@BeforeEach` |
| **`any()` everywhere** | Proves the method was called, not that it was called *correctly* | Exact values where they're part of the contract |
| **Changing the test to match a failure** | Turns a bug report into a regression lock | Decide first: is the code wrong, or the spec? |
| **`lenient()` / `@MockitoSettings(strictness = LENIENT)` by default** | Hides dead stubs and unexpected paths | Fix the stub, or move it where it's used |

---

## 11. Testability smells in this codebase

Hard-to-test code is usually telling you something about its design. Here are three places where that happens in this repo.

### Static calls into adapters from application services

`AuthenticateUserService.java:46`:
```java
RefreshToken refreshToken = JwtUtils.buildRefreshToken(user, tokenPair);
```
`RefreshTokenService.java:36`:
```java
String jtiHash = HashUtils.sha256(sub.jti());
```

`JwtUtils` and `HashUtils` live in `adapters/out/`. The service calls them statically, so there's **no seam** to replace them. To unit-test `login`, you either:
- let the real `JwtUtils.buildRefreshToken` run, so your test depends on JWT parsing and the token issuer mock has to return a *parseable* token; or
- use `mockStatic(JwtUtils.class)`, which works but couples the test to a private implementation detail.

The fix is structural: put that behaviour behind an outbound port (or into `TokenIssuerPort`). This is the same boundary violation chapter 03 describes. The test is just where you *feel* it first.

### `Instant.now()` inside domain logic

`AbstractMessageCreationStrategy.java:44`, `ImageMessageCreationStrategy.java:29`, `AbstractChannelCreationStrategy.java:34`, `User.java:35`. You can't assert an exact timestamp; the best you can do is `isBetween(before, after)`. Injecting `java.time.Clock` (`Instant.now(clock)`) lets tests use `Clock.fixed(...)` and assert exact values.

### Public mutable state

`WsSessionStore.java:11` exposes a public mutable map. Any test (or class) can reach in and change it, which breaks *Independent*.

**The rule: if a unit test needs `mockStatic`, reflection, or `lenient()` to get started, fix the design before you write the test.**

---

## 12. Running tests

```bash
./mvnw test                                        # all unit tests
./mvnw test -Dtest=UserServiceTest                 # one class
./mvnw test -Dtest=UserServiceTest#register*       # methods matching a pattern
./mvnw test -Dtest='*CreationStrategyTest'         # classes matching a pattern
./mvnw spotless:apply                              # format before committing (CI checks it)
./mvnw verify && xdg-open target/site/jacoco/index.html   # coverage report
```

Put each test in the **same package** as the class under test, under `src/test/java`. Then it can reach package-private members if it ever has to (prefer the public API).

Use coverage to find *untested branches*. Don't use it as a target: 100% line coverage with weak asserts is worth less than 70% with strong ones.

---

## 13. Review checklist

This is the rubric I'll use when you ask me to review an exercise. Use it on yourself first.

**Behaviour coverage**
- [ ] Every rule/branch in the method has a test that fails *only* that rule
- [ ] Boundaries tested on both sides
- [ ] Happy path asserts the *output* (return value and/or captured port argument), not just "no exception"
- [ ] Every enum → behaviour mapping has an exhaustiveness test where relevant

**Assertion strength**
- [ ] Expected values come from the spec, not the implementation
- [ ] Exceptions: type **and** the meaningful part (message fragment, error code, details)
- [ ] Negative paths prove no side effect (`never()` / `verifyNoInteractions`)
- [ ] Exact argument values (or captors) where the argument is part of the contract

**Design**
- [ ] One behaviour per test; the name states condition + outcome
- [ ] Value objects built for real; only ports mocked
- [ ] No unused stubs, no `lenient()` without a comment explaining why
- [ ] Parameterized where cases differ only by data
- [ ] Helpers hide noise but keep the values that matter visible

**Hygiene**
- [ ] Deterministic (no `isEqualTo(Instant.now())`, no ordering assumptions on `Set`/`HashMap` output)
- [ ] Independent (no shared mutable state)
- [ ] Passes `./mvnw spotless:check`
- [ ] A red test caused by a real bug is **kept red** (or `@Disabled("BUG: …")` with a reason) and reported. Never "fixed" by weakening the assertion.

---

## Where else this applies

- The same captor-on-port pattern works for `ChannelService.addMember` (`increaseTotalMembers` + `addMembers`) and `RefreshTokenService.refresh` (the stored `RefreshToken`).
- `@EnumSource` exhaustiveness applies to `ChannelType` → `ChannelCreationStrategy` in `ChannelService`, `ContentType` → `MessageCreationStrategy`, and `ErrorCode` → `HttpStatus`.
- The `@WebMvcTest` setup you work out for `MessageController` is reusable for every other controller. Only the `@MockitoBean` use cases change.
