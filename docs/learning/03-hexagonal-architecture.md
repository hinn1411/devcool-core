# 03 — Hexagonal Architecture

The discipline here is real. 81 domain files, one framework import. That's better than most codebases that claim this architecture.

So this chapter isn't "you're doing it wrong". It's about the three specific places the boundary leaks, why each leak was invisible, and how to make the rules enforce themselves instead of relying on review.

---

## The rule in one line

Dependencies point **inward**. `adapters → application → domain`. Never the reverse, never sideways.

Everything below is a violation of that single arrow.

---

## 1. A Spring web type inside a domain port

**The concept.** A port's parameter types *are* its contract. If the contract mentions a transport type, the use case can only ever be driven by that transport.

**In your code** — `domain/media/port/in/command/UploadMediaCommand.java`:

```java
package com.devcool.domain.media.port.in.command;

import org.springframework.web.multipart.MultipartFile;

public record UploadMediaCommand(
    MultipartFile file, long size, String contentType, Integer userId, Integer channelId) {}
```

This is the only non-Lombok framework import in all 81 domain files, which is exactly what makes it worth studying.

It propagates: `MediaService.java:22` imports `MultipartFile` and calls `file.isEmpty()`, `file.getContentType()`, `file.getInputStream()` — so the *application* layer now handles a servlet type too.

**Why it matters.** Three concrete costs:

1. The media use case cannot be driven from a WebSocket upload, a batch import, or a queue consumer without fabricating a `MultipartFile`.
2. `MediaServiceTest.java:24` is forced to import `org.springframework.mock.web.MockMultipartFile` — a *servlet test double* to test a *domain use case*.
3. `CLAUDE.md:112` explicitly claims the opposite: *"Controller → Service: `UploadMediaCommand` record (domain object, no HTTP types)"*. The written rule and the code disagree.

**The fix.** Express what the domain actually needs:

```java
public record UploadMediaCommand(
    String filename,
    String contentType,
    long size,
    Supplier<InputStream> content,
    Integer userId,
    Integer channelId) {}
```

`MediaDtoMapper` then adapts `MultipartFile` into it — which is precisely an adapter's job.

**A second tell in the same record.** It carries `file`, `size` *and* `contentType`, where `MediaDtoMapper.java:16` derives the last two from the first:

```java
return new UploadMediaCommand(file, file.getSize(), file.getContentType(), userId, channelId);
```

Three fields encoding two facts. They can disagree — and at `MediaService.java:43-46` they already almost do, because the code validates `file.getContentType()` and reports `command.contentType()`. The duplication is a symptom: the author sensed `MultipartFile` didn't belong and worked around it by copying values out, instead of removing it.

---

## 2. The application layer importing adapter utilities

**This is the one I initially missed**, because I grepped `domain/` for framework imports and stopped there. The arrow can be violated from the other side too.

**In your code:**

```
application/service/AuthenticateUserService.java:3
    import com.devcool.adapters.out.jwt.util.JwtUtils;

application/service/RefreshTokenService.java:3
    import com.devcool.adapters.out.crypto.util.HashUtils;
application/service/RefreshTokenService.java:4
    import com.devcool.adapters.out.jwt.util.JwtUtils;
```

The dependency arrow now points `application → adapters/out` — the exact inversion this architecture exists to prevent.

**Why static utilities made it invisible.** You were careful with constructor injection — every collaborator in these services is a port interface. But `static` calls don't appear in a constructor signature, can't be mocked, and can't be swapped. So "I only depend on interfaces" became false without anything in the class *looking* different.

**Why it matters beyond purity.** `JwtUtils.buildRefreshToken` contains a **domain policy** — the 7-day refresh lifetime at `JwtUtils.java:73`:

```java
.expiredTime(Instant.now().plus(7, ChronoUnit.DAYS))
```

…which disagrees with `TokenIssuerAdapter.java:36` (`refreshTtlSec = 1209600` = 14 days). A business rule is living in an adapter helper, in two places, with two values. See chapter 05 §7.

**The fix.** `HashUtils.sha256` belongs behind a port (`HashPort`) or inside the adapter that needs it. Token lifetime belongs in the domain as a policy value. If a static helper is genuinely pure and infrastructure-free, move it to `domain/common/`.

**And one more** — `RefreshTokenService.java:21`:

```java
import org.springframework.security.authentication.CredentialsExpiredException;
```

Spring Security in the application layer, throwing a framework exception that bypasses your whole error taxonomy (chapter 05 §5).

---

## 3. One adapter depending on another

**In your code** — `adapters/out/realtime/immemory/WsRealtimeEmitterAdapter.java:3,19`:

```java
import com.devcool.adapters.in.websocket.handler.WsSessionStore;
...
private final WsSessionStore sessionStore;
```

An **outbound** adapter depends on an **inbound** one, laterally, with no port between them.

**Why it matters.** Adapters should be leaves — replaceable independently. You cannot swap this emitter for a Redis pub/sub implementation without also owning the inbound session registry. The coupling isn't visible in the hexagon diagram at all.

**The signal to read.** Shared state between two adapters almost always means **a port is missing**. Here it's a `SessionRegistryPort` — or the session map belongs in the outbound adapter, populated through a port call from the handler.

---

## 4. Two ports for one concept

`domain/auth/port/out/LoadUserPort.java` and `domain/user/port/out/UserPort.java` both load users. They're implemented by two different adapters (`AuthAdapter`, `UserAdapter`) over the same `UserRepository`.

Then the *channel* context uses the *auth* one — `AbstractChannelCreationStrategy.java:3,20`:

```java
import com.devcool.domain.auth.port.out.LoadUserPort;
...
protected final LoadUserPort userPort;
```

while `ChannelService.java:33` injects `UserPort`. The same service tree loads users through two abstractions backed by two adapters.

**Why it matters.** The two implementations have drifted: `AuthAdapter.loadById` has no `@Transactional`; `UserAdapter.findById` has `@Transactional(readOnly = true)`. Identical operations, different transactional semantics, chosen by accident rather than decision.

**The rule.** A port is named for **what the hexagon needs**, not for the feature that first needed it. Duplicating a port "because this is a different feature" produces divergent behaviour for the same query. If the channel context genuinely needs a narrower view, declare `domain/channel/port/out/ChannelMemberLookupPort` — don't reach into another context's auth port.

---

## 5. The rules you wrote down are not the rules that run

`CLAUDE.md:150-158` lists seven PR review guidelines. Six are mechanically checkable:

> - domain classes must not import Spring/JPA annotations
> - adapters must not contain business logic
> - services implement inbound ports and depend only on outbound port interfaces
> - new use cases must define an inbound port interface in `domain/*/port/in/`
> - …

**None is enforced.** Violations §1, §2 and §3 are all breaches of rules this project wrote down and then checked by hand.

**The fix — the highest-leverage single addition to this codebase.** ArchUnit turns each bullet into a test:

```java
@Test
void domainDependsOnNoFramework() {
  noClasses().that().resideInAPackage("..domain..")
      .should().dependOnClassesThat()
      .resideInAnyPackage("org.springframework..", "jakarta.persistence..", "software.amazon..")
      .check(new ClassFileImporter().importPackages("com.devcool"));
}

@Test
void applicationDependsOnNoAdapters() {
  noClasses().that().resideInAPackage("..application..")
      .should().dependOnClassesThat().resideInAPackage("..adapters..")
      .check(new ClassFileImporter().importPackages("com.devcool"));
}

@Test
void adaptersDoNotDependOnEachOther() {
  noClasses().that().resideInAPackage("..adapters.out..")
      .should().dependOnClassesThat().resideInAPackage("..adapters.in..")
      .check(new ClassFileImporter().importPackages("com.devcool"));
}
```

Three tests, about fifteen lines, and all three violations in this chapter fail the build immediately. They also make the rules *true going forward* without anyone remembering them at review time.

**The principle: a written convention that isn't executable is a convention that will be violated.** Not through carelessness — through the completely ordinary act of writing `import` and letting the IDE resolve it.

---

## 6. Where the architecture genuinely pays off

Worth naming, because the chapter is otherwise a list of leaks.

The `senderId` refactor (`docs/improvements/2026-09-19-list-messages-improvements.md`) changed `Message` from holding a full `User` and `Channel` to holding two `Integer`s. Query cost went from `7 + N` to a flat 3, and a password hash stopped being reachable from a chat message.

That refactor touched **four files** and broke nothing, because `MessagePort` is an interface and `MessageService` never knew how a message was stored. That is the payoff. The boundary is worth defending.

The same is true of the domain's 80/81 record: it *is* nearly pure, and the one violation is fixable in an afternoon.

---

## Checklist

- [ ] No `domain/**` file imports a framework type
- [ ] No `application/**` file imports `com.devcool.adapters.*`
- [ ] No `adapters.out.*` file imports `adapters.in.*` (or vice versa)
- [ ] No static utility call crosses a layer boundary
- [ ] One concept, one outbound port
- [ ] Business policy (TTLs, limits, rules) lives in the domain, not in an adapter helper
- [ ] ArchUnit tests exist for each of the above
