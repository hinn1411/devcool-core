# 12 — Integration Testing: Real Postgres, Real Context, Real HTTP

The question this chapter answers: **which bugs can only a test against the real database and the real Spring context catch, and how do you write those tests so they stay fast, independent and honest?**

Chapter 07 covered the bottom of the pyramid. This chapter covers the two layers above it. Chapter 13 is where you practise, and four of its exercises *are* P1-T11 and P1-T12.

Versions are the ones `pom.xml` gives you: Spring Boot 3.5.6, which manages **Testcontainers 1.21.3** and Awaitility 4.2.2. Nothing below needs a version number in the pom.

---

## 1. What only an integration test catches

A unit test mocks the ports. So everything *behind* a port, and everything *in front of* the inbound port, is invisible to it. In this repo that's a lot of code:

| What | Where | Why a unit test can't see it |
|---|---|---|
| JPQL and native SQL | `MessageRepository.java:12-26`, `RefreshTokenRepository.java:11-36` | Mockito returns what you stub. The query never runs, so a wrong `<`, a missing `deletedTime IS NULL` or a typo in a column name passes. |
| The schema itself | `V1__baseline.sql`, `V2__message_channel_id_index.sql` + `ddl-auto=validate` | Only a real Flyway run followed by Hibernate's `validate` proves that migrations and entities agree. |
| Constraint names the code depends on | `ApiExceptionHandler.java:102` matches `uk_member_channel_user` (`V1__baseline.sql:79`) | Rename the constraint in a migration and the 409 silently becomes a 500. No unit test has a constraint. |
| `@Modifying` bulk updates | `ChannelRepository.java:17-37` | They skip the persistence context. Whether that matters depends on what's loaded in the same transaction (§6). |
| Transaction boundaries | `@Transactional` on every service method | When the INSERT actually runs, and where a constraint violation surfaces, is decided at flush/commit time. |
| The security filter chain + real JWTs | `SecurityConfig.java:30-55`, `JwtAuthFilter` | `@WebMvcTest` with `.with(user("7"))` skips token parsing and the `tokenVersion` check entirely. |
| Cookies across requests | `AuthController.java:187-226` | The refresh flow is three requests sharing state in the DB. No single-method test covers it. |

Chapter 06 §4 ended its bug table with "Login → refresh round trip — 1 integration test". That's the shape of the whole category: **the bug lives between components, so the test has to include both sides.**

**The rule: write an integration test when the behaviour you need to prove lives in SQL, in the schema, in a transaction boundary, or between two components. Everything else stays a unit test.**

---

## 2. Maven: Surefire, Failsafe and the `IT` suffix

Two plugins run tests, in different phases:

| Plugin | Phase | Picks up by default | Fails the build |
|---|---|---|---|
| **Surefire** | `test` | `*Test`, `Test*`, `*Tests`, `*TestCase` | immediately |
| **Failsafe** | `integration-test` + `verify` | `*IT`, `IT*`, `*ITCase` | in `verify`, *after* `post-integration-test` has run (that's the "safe" part: containers can still be torn down) |

In this repo Failsafe only exists inside a profile, `pom.xml:249-273`:

```xml
<profile>
  <id>integration-test</id>
  <activation><property><name>it</name></property></activation>
  ... maven-failsafe-plugin ...
</profile>
```

So:

```bash
./mvnw test             # Surefire only: unit tests
./mvnw verify           # Surefire only: the profile isn't active, so *IT classes never run
./mvnw -Dit verify      # Surefire + Failsafe: unit tests, then ITs
./mvnw -Dit verify -Dit.test=MessageAdapterIT   # one IT (Failsafe's property is it.test, not test)
```

Two consequences:

1. **Name every integration test `*IT`.** A class called `MessageRepositoryTest` that starts a container runs in Surefire, on every `./mvnw test`, and makes the fast loop slow. `.claude/rules/testing.md` already says this.
2. **`DevCoolApplicationTests` is named for Surefire.** P1-T11 converts it. Rename it `DevCoolApplicationIT` when you give it a container, or it will try to start Postgres during `./mvnw test`.

CI today runs `./mvnw -B -U -DskipITs verify` (`.github/workflows/ci.yml:58`). `-DskipITs` is a Failsafe property, but Failsafe isn't active without `-Dit`, so the flag currently does nothing. P1-T14 changes this line.

**The rule: the class name decides which plugin runs it. Fast tests end in `Test`, anything with a container ends in `IT`.**

---

## 3. Testcontainers and `@ServiceConnection`

### The dependencies (P1-T11)

All three are version-managed by Boot 3.5.6, so no `<version>` is needed:

```xml
<dependency>
  <groupId>org.springframework.boot</groupId>
  <artifactId>spring-boot-testcontainers</artifactId>
  <scope>test</scope>
</dependency>
<dependency>
  <groupId>org.testcontainers</groupId>
  <artifactId>junit-jupiter</artifactId>
  <scope>test</scope>
</dependency>
<dependency>
  <groupId>org.testcontainers</groupId>
  <artifactId>postgresql</artifactId>
  <scope>test</scope>
</dependency>
```

(Testcontainers 2.x renamed these artifacts to `testcontainers-postgresql` etc. That arrives with Boot 4. If you read a 2.x tutorial, the artifact ids won't match.)

### What `@ServiceConnection` replaces

Before Boot 3.1 you started a container and copied its random port into Spring properties:

```java
@DynamicPropertySource
static void props(DynamicPropertyRegistry r) {
  r.add("spring.datasource.url", postgres::getJdbcUrl);
  r.add("spring.datasource.username", postgres::getUsername);
  r.add("spring.datasource.password", postgres::getPassword);
}
```

`@ServiceConnection` does that for you. Spring Boot sees a `PostgreSQLContainer`, builds `JdbcConnectionDetails` from it, and the datasource, Flyway and JPA all pick those up. It's the same mechanism that `docker/local/compose.yaml` uses through its `org.springframework.boot.service-connection: postgres` label. Locally, Compose supplies the connection; in tests, the container does.

### The image: pgvector, not plain postgres

Test against what you run (ADR-0009: Aurora PostgreSQL + pgvector). The local DB is `pgvector/pgvector:pg16` since P1-T02. `PostgreSQLContainer` refuses unknown images, so declare the substitution:

```java
static final DockerImageName PGVECTOR =
    DockerImageName.parse("pgvector/pgvector:pg16").asCompatibleSubstituteFor("postgres");

static final PostgreSQLContainer<?> POSTGRES = new PostgreSQLContainer<>(PGVECTOR);
```

### One container for the whole run

There are two lifecycles, and the difference is important:

| Pattern | Container lives | Cost with 10 IT classes |
|---|---|---|
| `@Testcontainers` + `@Container static` field, per test class | one test class | 10 container starts (~2–4 s each) |
| **Singleton**: static field started once in a static initializer, never stopped by you | the whole JVM (Ryuk removes it at exit) | 1 container start |

The singleton is what P1-T11 asks for ("a reusable container"):

```java
public abstract class AbstractIntegrationTest {
  @ServiceConnection
  static final PostgreSQLContainer<?> POSTGRES = new PostgreSQLContainer<>(PGVECTOR);

  static {
    POSTGRES.start();
  }
}
```

With one base class this is all you need. Once `@DataJpaTest` classes need the same container, move the declaration into a shared type (§5).

There's no `@Testcontainers` or `@Container` here on purpose. Those annotations would *stop* the container after each class, and the next class would get a fresh one with a different port. That breaks the context cache (§4).

### "Reusable" means two different things

- **Within one JVM run**: the singleton above. Always do this.
- **Across runs**: `.withReuse(true)` plus `testcontainers.reuse.enable=true` in `~/.testcontainers.properties` keeps the container alive *after* the JVM exits, so the next `./mvnw -Dit verify` skips the start. It's a developer-machine convenience only. CI never has the properties file, so the flag does nothing there. Data also survives between runs, so your tests must not assume an empty DB (§6).

### Why not H2

The phase file says "Testcontainers vs H2: test against what you run". In this repo that's concrete:

- `RefreshTokenRepository.java:13-21` is native SQL using Postgres `now()`.
- `UserRepository.java:44` is native `SELECT id FROM app_user WHERE id in :userIds`.
- V1 uses `timestamp(6) with time zone` and `CHECK` constraints. H2's "PostgreSQL mode" emulates some of this, not all, and not the same way.
- P4/P5 add `vector` columns. H2 has no pgvector.

A test that's green on H2 tells you the code works on H2.

### Things that just work

- **Docker Compose stays off in tests.** `spring-boot-docker-compose` is on the classpath, but `spring.docker.compose.skip.in-tests` defaults to `true`. Your `local` profile isn't active either.
- **Flyway runs.** The default profile has Flyway on, so the context starts by migrating the container from V1. That run *is* the smoke test for P1-T01.

**The rule: one pgvector container per JVM, wired with `@ServiceConnection`, started in a static block that nothing stops.**

---

## 4. The Spring test context cache: where the speed comes from

Starting the container takes seconds. Starting the Spring context takes seconds too, and you pay that per *distinct configuration*, not per class.

Spring's TestContext framework caches each `ApplicationContext` by a **key** built from the test's configuration:

- the `@SpringBootTest` / slice annotation and its attributes (`webEnvironment`, `properties`, `classes`)
- `@ActiveProfiles`, `@TestPropertySource`, `@Import`
- **every `@MockitoBean` / `@MockitoSpyBean`** (each different set is a different key)
- `@DynamicPropertySource` methods, context customizers (which is what `@ServiceConnection` registers)

Two IT classes with the same key share one context. Two with different keys mean two contexts. By default up to 32 are kept, each holding its own Hikari pool against the same container.

What splits the cache, in practice:

| Change in one IT class | Effect |
|---|---|
| `@MockitoBean StoragePort storage;` | New context, just for that class |
| `@SpringBootTest(properties = "x=y")` | New context |
| `@ActiveProfiles("test")` on some classes but not others | Two contexts |
| `@DirtiesContext` | The context is closed and rebuilt for the next class |

So: **put every shared annotation on one base class, and keep subclasses free of configuration.**

```java
@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
public abstract class AbstractIntegrationTest { ... container as in §3 ... }
```

Then `@DataJpaTest` classes form a second, smaller context (§5), and that's fine. Two contexts for the whole suite is a good result.

### Where the test configuration goes: an important trap

`src/test/resources/application.properties` doesn't *add to* `src/main/resources/application.properties`. **It replaces it.** Both are `classpath:application.properties`, test resources come first on the classpath, and Spring Boot loads the first match. You'd lose `ddl-auto=validate`, the multipart limits, `aws.region`, all of it.

Use a profile-specific file instead, which is merged on top of the main one:

```properties
# src/test/resources/application-test.properties
# Base64 of at least 32 bytes each (TokenIssuerAdapter rejects shorter keys). Test-only values.
security.jwt.access-secret=dGVzdC1hY2Nlc3Mtc2VjcmV0LWF0LWxlYXN0LTMyLWJ5dGVzIQ==
security.jwt.refresh-secret=dGVzdC1yZWZyZXNoLXNlY3JldC1hdC1sZWFzdC0zMi1ieXRlcyE=
aws.s3.bucket=devcool-test
```

(Generate your own with `head -c 32 /dev/urandom | base64`. They're test values, so committing them is fine. `local.env` stays untouched.)

Chapter 06 §1 recorded exactly this failure: `${JWT_ACCESS_SECRET}` can't be resolved because there's no `src/test/resources/`.

**The rule: one base class carries all the configuration; subclasses add tests, never annotations. Every `@MockitoBean` you add costs a context start.**

---

## 5. Choosing the slice

| Annotation | Loads | Transaction per test | Use it for |
|---|---|---|---|
| `@DataJpaTest` | JPA, Flyway, repositories, `TestEntityManager`. No services, no web, no security | **yes, rolled back** | Repository queries, entity mapping, constraints |
| `@DataJpaTest` + `@Import(MessageAdapter.class, MessageMapperImpl.class)` | the above + the adapter you name | yes, rolled back | **Testing the outbound port implementation** (the hexagonal contract) |
| `@SpringBootTest` (`MOCK` env) + `@AutoConfigureMockMvc` | everything; MockMvc instead of a server | no (unless you add it) | HTTP contract through the real filter chain, services and DB |
| `@SpringBootTest(webEnvironment = RANDOM_PORT)` | everything + a real Tomcat | no, and *can't* be (another thread) | WebSockets, real HTTP clients. Not needed in P1 |

### `@DataJpaTest` and the container

`@DataJpaTest` is meta-annotated with `@AutoConfigureTestDatabase`. Since Boot 3.4 its default is `replace = NON_TEST`: replace the datasource with an embedded one **unless it already points at a test database**, and a `@ServiceConnection` container counts as one. So with the container from §3 you don't need `replace = NONE`. (It's harmless, and many tutorials written before 3.4 include it.) Without a container it would try to start H2, fail to find it, and stop.

`@DataJpaTest` needs the container too, so share it. Put the container in its own type and import it from both bases:

```java
// one container declaration, used by every IT
interface PostgresContainer {
  @ServiceConnection
  PostgreSQLContainer<?> POSTGRES = startedPostgres();   // static helper that calls start()
}

@DataJpaTest
@ImportTestcontainers(PostgresContainer.class)
abstract class AbstractJpaIT {}
```

(`@ImportTestcontainers` registers the static `Container` fields of the named class. Interface fields are implicitly static. Either this style or the static field on the base class from §3 is fine. Pick one and use it everywhere.)

### What MockMvc is and isn't

`@AutoConfigureMockMvc` on a full context means the request goes through the **real** `SecurityFilterChain`, `JwtAuthFilter`, controller, service, adapter and Postgres. It doesn't go through Tomcat, so there's no real socket, and the request runs **on the test's thread**. That last fact matters for transactions (§6).

**The rule: `@DataJpaTest` to prove SQL and the outbound adapters; `@SpringBootTest` + MockMvc to prove a user-visible flow end to end. Don't pay for the full context to test one query.**

---

## 6. Test data and isolation

Integration tests share one database. The *Independent* in FIRST (07 §1) now has to be engineered on purpose.

### Option A: roll back every test

`@DataJpaTest` (and `@Transactional` on any test class) opens a transaction before each test and rolls it back after. Nothing is ever committed, so every test starts with the same data. It's cheap and automatic.

It also comes with three traps, and each one hides a real bug class.

**Trap 1: the INSERT never happens.** Every entity here uses `GenerationType.SEQUENCE` (e.g. `MemberEntity.java:24`). With a sequence, Hibernate doesn't need the INSERT to get the id, so `save()` only *queues* it. The SQL runs at flush: before a query that touches the table, on `flush()`, or at commit. A rolled-back test never commits.

```java
// Looks like a constraint test. Passes for the wrong reason, or fails confusingly.
memberRepository.save(duplicateOf(existing));   // queued, no SQL yet
// … test ends, rollback: uk_member_channel_user was never checked
```

Use `saveAndFlush(...)`, or `entityManager.flush()`, whenever the test is *about* what the database does.

**Trap 2: you read your own cache, not the database.** After `save`, the entity sits in the persistence context. `findById` returns that same Java object without any SQL. A broken column mapping, or a `@Modifying` update the context doesn't know about, stays invisible:

```java
channelRepository.updateChannelInfo(id, "renamed", ChannelType.FORUM, null); // SQL UPDATE
channelRepository.findById(id).get().getName();   // can still be the OLD name: cached entity
```

When the assertion is about what's *stored*, do `flush()` and then `clear()` first, so the next read really hits Postgres. `TestEntityManager` has `flush()`, `clear()` and `persistFlushFind()` for exactly this.

**Trap 3: the transaction swallows the production boundary.** In a `@Transactional` `@SpringBootTest`, MockMvc runs on the test thread, so the service's `@Transactional` *joins* the test's transaction instead of starting and committing its own. Anything that only fails **at commit** (deferred INSERTs hitting a unique constraint, for example) never fails in the test. And because nothing commits, a second thread (§9) can't see any of the data.

Also: Postgres `now()` is the *transaction start* time. Inside one long test transaction, every `now()` returns the same instant (it affects `RefreshTokenRepository.consumeIfValid`).

### Option B: commit for real, clean up explicitly

For `@SpringBootTest` flows, let each request commit as it does in production, and reset the tables before each test:

```java
@Autowired JdbcTemplate jdbc;

@BeforeEach
void cleanDatabase() {
  jdbc.execute("""
      TRUNCATE refresh_token, media, message, member, topics_of_channels,
               channel, friend_request, auth_provider, topic, app_user
      CASCADE""");
}
```

- Clean **before** each test, not after. A test that crashed halfway still leaves the next one a clean DB, and with container reuse (§3) you start clean even if the last run didn't.
- Don't truncate `flyway_schema_history`.
- The ids don't restart. That's fine as long as tests never hard-code ids (see below).

### Option C: unique data per test

Give every row a value no other test uses (`"user-" + UUID.randomUUID()`) and never assume the table is empty. It's the most robust option and the only one that survives parallel test execution, but "count all rows" assertions become impossible.

### Recommendation for this repo

- `@DataJpaTest` ITs: Option A, with `flush()`/`clear()` discipline.
- `@SpringBootTest` ITs: Option B in the base class.
- **Never hard-code ids.** Sequences use `increment by 50` (`V1__baseline.sql:8-15`) and keep counting across tests and runs. Capture the id the save returns.

### Seeding

| Way | Good for | Watch out |
|---|---|---|
| Through the API (`POST /register`, `/login`) | Full-flow tests: the data is exactly what production would create | Slow (BCrypt strength 12, `SecurityConfig.java:59`); couples setup to other endpoints |
| Repositories / `TestEntityManager` | Repository and adapter tests | You must build valid entity graphs yourself (`optional = false` relations) |
| `JdbcTemplate` / `@Sql` | Precise edge cases: soft-deleted rows, expired tokens, rows the domain can't produce | Bypasses entity validation; breaks if a column is renamed (but then the test *should* break) |

Hide each in a small fixture helper (`aUser()`, `aLoungeWith(creator, members...)`), the same way 07 §2 hides noise, while keeping the values that matter visible.

**The rule: if the test is about the database, flush and clear before you assert. If the test is about a flow, let it commit and clean up before the next test.**

---

## 7. Testing outbound adapters: the port is the contract

In hexagonal terms, the persistence adapter *implements* a port the domain owns: `MessageAdapter implements MessagePort`. The repository is an implementation detail of the adapter.

So the most valuable persistence IT calls **the port method** and asserts **the domain result**:

```java
@DataJpaTest
@Import({MessageAdapter.class, MessageMapperImpl.class, MediaMapperImpl.class})
class MessageAdapterIT extends AbstractJpaIT {
  @Autowired MessagePort messagePort;
  @Autowired TestEntityManager em;

  @Test
  void findMessages_returnsNewestFirstBelowCursor() {
    // seed 5 messages in channel A, 1 in channel B, 1 soft-deleted in A
    em.flush(); em.clear();

    List<MessageItem> page = messagePort.findMessages(channelA, cursor, 3);

    assertThat(page).extracting(MessageItem::getId).containsExactly(/* expected ids, from the spec */);
  }
}
```

This catches bugs a pure repository test would miss: the mapper dropping a field, `PageRequest.of(0, size)` being off by one, or the adapter swapping arguments.

Testing the repository directly is still worth it when the query has edge cases the adapter doesn't expose.

### What to assert on a query

Use the same techniques as 07 §9, applied to SQL:

- **Filtering**: rows that must be excluded are *present in the seed* (another channel, a soft-deleted row, an expired token). A filter you never feed a negative case for isn't tested.
- **Ordering**: assert with `containsExactly`, not `containsExactlyInAnyOrder`. The order is the contract.
- **Boundaries**: cursor exclusive vs inclusive (`<` vs `<=`), `null` cursor, a page size exactly equal to the remaining rows.
- **Fetching**: after `em.clear()` and outside a transaction, can the result be used without a `LazyInitializationException`? `MessageRepository` uses `JOIN FETCH` for exactly this. Fetch-joining **to-one** relations with a `Pageable` is fine. Fetch-joining a **collection** with paging makes Hibernate page in memory (warning HHH90003004).

### Proving performance properties

Two tools turn "this query is efficient" into an assertion:

- **Statement count.** Turn on Hibernate statistics in the test profile (`spring.jpa.properties.hibernate.generate_statistics=true`), then read `SessionFactory.getStatistics().getPrepareStatementCount()` before and after the call. One page of messages should be one statement, not 1 + N.
- **The query plan.** `EXPLAIN` runs fine through `JdbcTemplate`. On a tiny table Postgres prefers a sequential scan even when the index exists, so for a plan assertion either seed enough rows or `SET enable_seqscan = off` in that transaction, then assert the plan mentions `ix_message_channel_id_id` (the name from `V2__message_channel_id_index.sql`). It proves P1-T03's index is actually usable by the query it was made for.

**The rule: test the adapter through its port, seed the rows that must be excluded, and assert order as part of the contract.**

---

## 8. Full-stack HTTP tests

### Getting a real token

`@WebMvcTest` used `.with(user("7"))`. That skips `JwtAuthFilter`: no signature check, no `tokenVersion` lookup (`JwtAuthFilter.java:41-56`). In an IT, use a **real** bearer token. There are two ways:

- **Log in through the API**: `POST /api/v1/auth/login`, read `$.data.accessToken`. This is the most realistic option, and you need it anyway for the refresh-cookie flow.
- **Issue one directly**: `@Autowired TokenIssuerPort` and call `issue(user)` for a user you saved. It's faster and skips BCrypt. The token is still signed and validated for real.

Wrap whichever you pick in a helper (`bearerFor(user)`), so each test only states *who* is calling.

### Cookies: MockMvc is not a browser

`AuthController` sets the refresh cookie with `HttpOnly; Secure; SameSite=Strict; Path=/api/v1/auth` (`AuthController.java:218-226`). A browser would apply those rules. **MockMvc applies none of them.** It doesn't store cookies between requests, doesn't check `Path`, and doesn't drop `Secure` cookies over plain HTTP.

That cuts both ways:

1. You must **carry the cookie yourself**: read it from the login response, then `.cookie(new Cookie("rt", value))` on the refresh request.
2. A wrong `Path` won't break the test the way it breaks a browser. That was exactly bug #7: the cookie path was `/api/v1/auth/refresh` and the endpoint was `/refresh_token`. **Assert the attributes explicitly** on the `Set-Cookie` header:

```java
String setCookie = loginResult.getResponse().getHeader(HttpHeaders.SET_COOKIE);
assertThat(setCookie)
    .contains("Path=/api/v1/auth")
    .contains("HttpOnly")
    .contains("Secure")
    .contains("SameSite=Strict");
```

### Assert both sides: the response and the database

A full-stack test has a unique advantage: after the request, it can look at the rows. For "logout revokes the token", don't stop at `204`. Check that `refresh_token.consumed_time` is set and `app_user.token_version` went up, and that the **old access token is now rejected** on the next request. That last one is the behaviour the user cares about.

### External services

`S3ClientConfig` builds the `S3Client` and `S3Presigner` beans with `DefaultCredentialsProvider`, which resolves credentials **when a request is made**, not at startup. The context starts without AWS credentials, and no P1 IT touches media. If a later IT needs storage, replace `StoragePort` with a `@MockitoBean` **in the base class** (§4), or use LocalStack. Never call real AWS from a test.

**The rule: real tokens, explicit cookies, assert the cookie attributes a browser would enforce, and check the database after the response.**

---

## 9. Concurrency integration tests

Some bugs only exist when two transactions overlap. Unit tests run one thread against mocks, so they can't see them. The test conventions (`.claude/rules/testing.md`) already set the rules:

- Start threads together with a `CountDownLatch`, so they really overlap.
- Run **≥ 20 iterations**: one lucky interleaving proves nothing.
- Assert **invariants** ("exactly one succeeded", "count never exceeds the cap"), not timings.
- Use Awaitility for anything asynchronous, never `Thread.sleep`.

```java
ExecutorService pool = Executors.newFixedThreadPool(2);
CountDownLatch start = new CountDownLatch(1);
Callable<Integer> attempt = () -> {
  start.await();
  return mockMvc.perform(post(...).header(AUTHORIZATION, bearer)).andReturn()
      .getResponse().getStatus();
};
Future<Integer> a = pool.submit(attempt);
Future<Integer> b = pool.submit(attempt);
start.countDown();
assertThat(List.of(a.get(), b.get())).containsExactlyInAnyOrder(200, 409);
```

Requirements that follow from §6:

- **The test class must not be `@Transactional`.** Each worker thread needs its own committed transaction, and the data the test seeded must be committed so the workers can see it.
- Clean up with Option B.
- Each worker's MockMvc call runs on the worker's thread, so each one gets its own service transaction. That's what you want.

### Check-then-act

The classic shape these tests find:

```java
if (!repository.exists(x)) {   // check
  repository.insert(x);         // act
}
```

Under Postgres's default `READ COMMITTED`, two transactions can both pass the check before either inserts. Only two things actually stop the second one: **a database constraint**, or **a lock taken by the check**. An IT is the only test that can show which of them, if any, protects a given code path.

**The rule: if correctness depends on two transactions not overlapping, prove it with a latch, 20+ iterations and an invariant. The database constraint is the real guard, and the IT proves it fires.**

---

## 10. Anti-patterns

| Anti-pattern | Why it hurts | Instead |
|---|---|---|
| **H2 "because it's faster"** | Proves the code works on a database you don't run | pgvector container, singleton |
| **Container per test class** | Seconds per class; new port → new context | One static container for the JVM |
| **`@MockitoBean` sprinkled across IT classes** | Each set is a new Spring context | Mock in the base class or not at all |
| **`@DirtiesContext` to "fix" leaking data** | Rebuilds the context; the leak is in the data, not the context | Truncate in `@BeforeEach` |
| **`src/test/resources/application.properties`** | Silently replaces the main file | `application-test.properties` + `@ActiveProfiles("test")` |
| **Asserting right after `save()`** | Reads the persistence-context cache, not the DB | `saveAndFlush` / `flush()` + `clear()` |
| **`@Transactional` on a full-flow or concurrency IT** | Hides commit-time failures; other threads see nothing | Let it commit; clean up |
| **Hard-coded ids** (`/channels/1`) | Sequences don't restart; ids change with test order | Use the id the setup returned |
| **Only the happy row in the seed** | The `WHERE` clause is never exercised | Seed the rows that must be excluded |
| **`Thread.sleep` for async** | Slow *and* flaky | Awaitility |
| **One IT per business rule** | Slow suite; the rule belongs in a unit test | ITs for wiring, SQL and flows; units for rules |
| **Calling real AWS / external APIs** | Cost, flakiness, secrets in CI | Fake the port; LocalStack if you must |

---

## 11. Running and CI

```bash
./mvnw -Dit verify                                # unit + all ITs (Docker must be running)
./mvnw -Dit verify -Dit.test=MessageAdapterIT     # one IT class
docker ps --filter label=org.testcontainers=true  # what Testcontainers has running
```

Startup cost to expect: the first IT pays for the container (a few seconds, plus the image pull on first use) and the Spring context. Each later class sharing that context should take milliseconds of overhead. If every class takes seconds, the cache is being split (§4). Spring logs every new context at `INFO`, so count them.

In CI (P1-T14): GitHub's `ubuntu-latest` runners have Docker, so Testcontainers works with no extra setup. Image pulls are the slow part, and caching them is part of P1-T14. Integration tests must **fail the build**. A skipped IT is the `|| true` of chapter 06 §2 under another name.

---

## 12. Review checklist

This is the rubric I'll use when you ask me to review an exercise in chapter 13.

**Scope**
- [ ] The test needs the DB, the context or HTTP to prove its behaviour. If not, it should be a unit test.
- [ ] The class name ends in `IT`
- [ ] It extends the shared base and adds no context-changing annotations

**Database honesty**
- [ ] Assertions about stored state happen after `flush()` + `clear()` (or across committed requests)
- [ ] Constraint tests force the SQL to run (`saveAndFlush` / `flush()`)
- [ ] The seed contains the rows the query must *exclude*
- [ ] Order asserted with `containsExactly` where order is the contract
- [ ] No hard-coded ids

**Flow**
- [ ] Real bearer tokens through the real filter chain
- [ ] Cookie attributes asserted on `Set-Cookie`, and cookies carried explicitly
- [ ] Response **and** resulting DB state asserted for state-changing calls
- [ ] Every authorization rule has its negative cases: 401 without a token, 403 for a non-member, 403 for the wrong role

**Isolation and determinism**
- [ ] Passes alone, in any order, and twice in a row against a reused container
- [ ] Concurrency tests: latch, ≥ 20 iterations, invariant assertions, not `@Transactional`
- [ ] No `Thread.sleep`
- [ ] A red test caused by a real bug is **kept red** (or `@Disabled("BUG: …")`) and reported, the same rule as 07 §13

---

## Where else this applies

- The `@DataJpaTest` + `@Import(adapter, mapper)` pattern works for every adapter in `adapters/out/persistence/`: `ChannelAdapter.loadChannels` has its own `limit + 1` cursor logic (`ChannelAdapter.java:34-56`) that has never run against a database.
- The concurrency harness from §9 is what P3-T03 ("concurrent retry race (IT)") needs for idempotent sends.
- The real-token helper from §8 is reused by every authz IT P3 adds ("Authz matrix for edit/delete/remove").
- `EXPLAIN` assertions (§7) are how you'd prove P4's pgvector index is actually used by the similarity query.
