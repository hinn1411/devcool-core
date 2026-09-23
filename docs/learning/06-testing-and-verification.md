# 06 — Testing & Verification

The question this chapter answers: **how did all of that survive?**

Not "why weren't there tests" — you can write tests, and the two real test classes here are good. The answer is more specific and more useful than that.

---

## 1. The test that proves the app starts runs nothing

**In your code** — `src/test/java/com/devcool/DevCoolApplicationTests.java`, in full:

```java
package com.devcool;

import org.springframework.boot.test.context.SpringBootTest;

@SpringBootTest
class DevCoolApplicationTests {}
```

**A JUnit 5 class with zero `@Test` methods is never instantiated.** The Spring context is never loaded. The one test whose entire job was to prove "the application boots" proves nothing — and it looks like it passes.

**Verify it yourself.** You already have the evidence. Run `./mvnw verify`:

```
Tests run: 9  -- MediaServiceTest
Tests run: 4  -- S3StorageAdapterTest
Tests run: 13, Failures: 0, Errors: 0, Skipped: 0
```

9 + 4 = 13. `DevCoolApplicationTests` contributes **zero**. The class is loaded, reported as passing, and executes nothing.

The specific action that broke it: Spring Initializr generates this class *with* a `contextLoads()` method. It was deleted.

Worse, it couldn't pass if it ran. There is no `src/test/resources/` at all, so `application.properties:23` (`security.jwt.access-secret=${JWT_ACCESS_SECRET}`) fails placeholder resolution, and there is no test datasource.

**Why this one matters most.** A context-load test catches bean wiring failures, missing configuration and constructor mismatches across the whole application for free. Earlier in this session I told you this class would catch constructor-arity mistakes during the `senderId` refactor. **That was wrong** — I asserted a safety net that doesn't exist. The refactor was fine, but nothing verified it.

**The fix.** Restore the method and give it a profile:

```java
@SpringBootTest
@ActiveProfiles("test")
class DevCoolApplicationTests {
  @Test
  void contextLoads() {}
}
```

plus `src/test/resources/application-test.properties` with an H2 or Testcontainers datasource and dummy secrets.

**The rule: a test that asserts nothing is worse than no test.** It occupies the slot where the real one would go, and it makes the coverage report look intentional.

---

## 2. The CI quality gate is `|| true`

**In your code** — `.github/workflows/ci.yml:53-55`:

```yaml
      - name: Static analysis
        run: |
          ./mvnw -B -q -DskipTests -DskipITs -Pstatic-analysis verify || true
```

The `|| true` discards the exit code. The step always passes.

This reframes something I said earlier in this session. I described the 110 SpotBugs findings as "a red baseline that hides real bugs". That was half right. The truth is worse: **the gate was deliberately neutered**, so it burns CI minutes, produces a green check mark, and enforces nothing.

And it *is* hiding real bugs. Inside those 110 findings:

- `DLS_DEAD_LOCAL_STORE` at `WsSubscribeService.java:34` and `:43` — the two dead stores that make a 53-query channel load pointless (chapter 01 §1)
- `PA_PUBLIC_MUTABLE_OBJECT_ATTRIBUTE` at `WsSessionStore.java:11` — the public mutable session map (chapter 04 §9)

SpotBugs found them. Nobody saw them.

### On the 110 findings: "suppress" vs "fix all" is a false choice

Most of the 110 are `EI_EXPOSE_REP`/`EI_EXPOSE_REP2` — a getter returning, or a constructor storing, a mutable object. They are generated *automatically* by `@Getter @Setter` on `Channel` (`List<Member>`), `Message` (`Media`), `Member` (`User`), and every JPA entity.

So the 110 are not 110 independent mistakes. They are **one design decision counted 110 times**: mutable, setter-exposed domain models.

- *Suppressing them* deletes the only automated signal pointing at that design.
- *Fixing them one by one* — defensive copies in 110 getters — adds noise and doesn't change the design.
- **Fixing the cause** collapses them: make the value-ish models records (`ChannelListItem`, `MessageItem`, `Media`, `TokenSubject`), make `Channel`/`Message`/`User` immutable with `List.copyOf` in the constructor, and the class largely disappears. `Instant` is immutable, so several remaining hits are false positives that belong in a small, *annotated and justified* `spotbugs-exclude.xml`.

### Why the gate failed on day one

The `static-analysis` profile also runs Checkstyle with `google_checks.xml` (`failsOnError=true`) and PMD with default rulesets (`failOnViolation=true`). `google_checks.xml` is a *formatting* ruleset that overlaps Spotless — which already runs separately at `ci.yml:50-51`. Default PMD rulesets include `ShortVariable` and `OnlyOneReturn`.

Three analysers, all defaults, all failing, at threshold Low and effort Max, before a single finding was triaged. `|| true` was the expedient escape.

**The rule: a quality gate has two valid states — enforcing, or absent.** `|| true` creates a third that is strictly worse than absent, because it advertises rigour that isn't there.

**The way out is ratcheting.** Baseline the current 110 (`excludeFilterFile`, or `failOnViolation=false` + `maxAllowedViolations`), fail the build on *anything new*, burn the baseline down. And configure **one** analyser at a time with a **curated** ruleset.

---

## 3. Two more CI steps that don't do what they say

**Integration tests cannot run, under any circumstance.** `ci.yml:57-58`:

```yaml
      - name: Build + Tests + ITs
        run: ./mvnw -B -U -DskipITs verify
```

The step is named "ITs" and the flag is `-DskipITs`. Even without it, the `integration-test` profile in `pom.xml` activates on `-Dit`, which appears nowhere in CI.

Meanwhile `ci.yml:17-19` carries the comment *"Testcontainers works out-of-the-box on GitHub runners"* — infrastructure planned for, commented about, never added.

**Sonar never runs.** `ci.yml:60`:

```yaml
        if: env.SONAR_TOKEN != '' && ...
```

`env.SONAR_TOKEN` reads the **job-level** `env:` block, which doesn't define it. The secret is bound at *step* level, three lines later. The condition is evaluated before that exists, so it is always false.

**Also:** `on: push: branches: ["**"]` with no `pull_request` trigger — a PR from a fork gets no CI at all. And JaCoCo runs `prepare-agent` + `report` with no `check` goal or threshold, so coverage is measured and never acted on.

---

## 4. Test where the risk is, not where testing is cheap

The 13 real tests cover `MediaService` and `S3StorageAdapter` — and they are **good tests**: `@ExtendWith(MockitoExtension.class)`, `ArgumentCaptor` assertions on the port contract, `verifyNoInteractions` on negative paths, real boundary cases (empty file, bad content type, bad extension, null key).

You can write tests. So the gap is a question of aim, not skill.

The media slice is the one use case with **no channel, no membership, no transaction, no concurrency, and no domain model**. It is the easiest thing in the codebase to test — one service, one mocked port.

Everything that broke lives in the code with collaborators and state.

**The bugs one small test would have caught:**

| Test | Catches | Cost |
|---|---|---|
| `User.isTokenVersionValid(3)` on `tokenVersion=4` is false | Logout doesn't log anyone out | 3 lines, no Spring |
| `assertThat(channel.getMembers()).hasSize(channel.getTotalOfMembers())` per strategy | Private-chat creator locked out of their own channel | 3 lines × 3 |
| `@ParameterizedTest @EnumSource(ErrorCode.class)` → status != 500 | 14 client errors returning 500 | 5 lines |
| `unsubscribe("c1", 999)` on an empty registry | NPE | 2 lines, no Spring |
| `getConnectionsByChannel(999)` returns empty, not null | NPE after commit → duplicate messages | 2 lines |
| `updateChannel` applies `boundaryType` | A channel can never be made private | 4 lines |
| Login → refresh round trip | Cookie path mismatch; refresh has never worked | 1 integration test |

Every one except the last is **plain JUnit + Mockito**. No database, no Testcontainers, no Spring context.

**The rule: the absence of test infrastructure is not why these are untested.** They're untested because the tests were written where testing was easy. Inverting that — aiming at collaborators, state and branches — is the whole skill.

---

## 5. Make the architecture rules executable

Covered in chapter 03 §5, repeated here because it belongs in the testing checklist: three ArchUnit tests (~15 lines) would fail the build on all three hexagonal violations, and keep failing for future ones.

```java
noClasses().that().resideInAPackage("..application..")
    .should().dependOnClassesThat().resideInAPackage("..adapters..")
```

`CLAUDE.md` lists seven review rules. Six are mechanically checkable. Zero are checked.

---

## 6. How to see each bug for yourself

Teaching material is worth less than a reproduction. For each major finding:

```bash
# Password hash exposure
curl -s -H "Authorization: Bearer $TOKEN" localhost:8080/api/v1/users/1 | jq '.data.password'

# Plaintext password echoed on failed login
curl -s -X POST localhost:8080/api/v1/auth/login \
  -H 'Content-Type: application/json' \
  -d '{"username":"realuser","password":"wrong"}' | jq '.details'

# N+1 on channel load — set this, then send one SUBSCRIBE and count statements
#   logging.level.org.hibernate.SQL=DEBUG

# Unreachable use cases — any port interface with no injection site
for p in WsUnsubscribeUseCase MediaPort; do
  echo "$p: $(grep -rl "$p" src/main/java | wc -l) files"
done

# Enum constants with no strategy
grep -rn "return ContentType\." src/main/java/com/devcool/application/service/chat/strategy/
# compare against ContentType.values() — TEXT, MARKDOWN, IMAGE, VIDEO

# The real static-analysis count, without the || true
./mvnw -B -q -DskipTests -DskipITs -Pstatic-analysis verify 2>&1 \
  | grep -cE "^\[ERROR\] (Medium|High|Low)"
```

---

## 7. A suggested order

1. **Restore `contextLoads()`** and add `src/test/resources/application-test.properties`. Highest value per line in the repo.
2. **Three ArchUnit tests.** Locks in chapter 03 permanently.
3. **Unit-test the pure logic that's currently broken** — `isTokenVersionValid`, `HttpErrorMapper`, `InMemoryConnectionRegistryAdapter`, the three channel strategies. No infrastructure needed; catches five live bugs.
4. **Remove `|| true`**, baseline the existing findings, fail on new ones.
5. **Fix `-DskipITs`** and add Testcontainers + one `@DataJpaTest`. That's the only thing that can catch the orphan-removal and constraint-race findings in chapter 01.
6. **One integration test for login → refresh → logout.** Catches the cookie path bug and everything like it.

---

## The meta-lesson

This repo has **nine** quality mechanisms configured: Spotless, Checkstyle, PMD, SpotBugs, JaCoCo, Sonar, Enforcer, Failsafe, and a Claude PR-review workflow.

The two that would have caught the bugs in these notes — **real tests** and **any enforcement at all** — are the two that don't work.

**Configuring quality tools is not the same as having quality signal.** When tools are added all at once, aspirationally, tool count ends up *inversely* correlated with tool enforcement: each one arrives failing, each failure gets worked around, and the workarounds are permanent.

Better: one tool, curated, enforcing, green. Then the next.

---

## Checklist

- [ ] `contextLoads()` exists and actually runs
- [ ] `src/test/resources/application-test.properties` exists
- [ ] No `|| true` in CI
- [ ] The IT step doesn't pass `-DskipITs`
- [ ] Sonar's condition can actually be true
- [ ] `pull_request` is a CI trigger
- [ ] ArchUnit tests for each written architecture rule
- [ ] Every directional predicate (`>=`, `==`) in a security path has a test
- [ ] Every enum-to-behaviour mapping has an exhaustiveness test
- [ ] Coverage has a threshold, or stop measuring it
