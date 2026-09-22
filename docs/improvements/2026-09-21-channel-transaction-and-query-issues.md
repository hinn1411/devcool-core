# Improvements — Channel Transaction Boundaries and Query Cost

**Date:** 2026-09-21
**Branch:** `refactor/message-creation-improvements`
**Scope:** `ChannelService.addMember`, `MemberAdapter`, `ChannelAdapter.findById`

Found while removing the redundant `@Transactional` from `MessageAdapter.save` (see `2026-09-19-list-messages-improvements.md`, now closed). Those three files were not touched on this branch — this document records the findings only.

Context for §1 and §2: the rule is `@Transactional` on application service methods, `@Transactional(readOnly = true)` for queries, and **never** on adapters (`lessons.md` §D.17). The message flow now follows it. The channel flow does not, and unlike the message case the gap is a live bug rather than dead weight.

---

## High

### 1. `ChannelService.addMember` is not atomic
**File:** `application/service/channel/ChannelService.java:70`

Two writes execute with no enclosing transaction:

```java
channelPort.increaseTotalMembers(channelId, command.userIds().size());  // line 95 — @Transactional on the adapter
return memberPort.addMembers(channelId, existingUserIds);               // line 96 — no transaction anywhere
```

`increaseTotalMembers` commits in its own adapter-level transaction (`ChannelAdapter.java:80`). If `addMembers` then fails, `TOTAL_OF_MEMBERS` stays permanently inflated with no member rows to match. Nothing repairs it — the damage is silent and accumulates with every failure.

The validation block above it (lines 76-93: user existence, channel existence, duplicate membership) also reads outside any transaction, so it is a TOCTOU window. Two concurrent `addMember` calls for the same user can both pass the `alreadyMembers.isEmpty()` check at line 90 and both insert.

**Fix:** add `@Transactional` to `addMember` so the checks and both writes form one unit. That alone does not close the concurrent-insert race — reads do not block each other under `READ COMMITTED` — so also add a unique constraint on `(CHANNEL_ID, USER_ID)` in `MEMBER` and let the duplicate surface as a constraint violation.

**Minor, same method:** line 95 passes `command.userIds().size()` while line 96 inserts `existingUserIds`. These are equal today only because of the guards at lines 72 and 77. Use `existingUserIds.size()` for both so the count cannot drift if those guards ever change.

### 2. `MemberAdapter.addMembers` uses `getReference` with no transaction
**File:** `adapters/out/persistence/member/MemberAdapter.java:40,49`

```java
ChannelEntity channelRef = em.getReference(ChannelEntity.class, channelId);   // line 40
...
.user(em.getReference(UserEntity.class, memberId))                            // line 49
```

`getReference` returns a lazy proxy that is only valid while a persistence context is open. `MemberAdapter` has no `@Transactional` anywhere in the class, and its only caller — `ChannelService.addMember` — has none either.

This works today only because `spring.jpa.open-in-view` is unset and therefore defaults to `true`, so an HTTP request has a session open for its whole lifetime. That safety net does not cover WebSocket frames, `@Async`, or scheduled tasks. Any future caller on one of those paths gets a `LazyInitializationException`.

**Fix:** fixing §1 fixes this too — `@Transactional` on `ChannelService.addMember` gives the proxies a real transaction. Do **not** fix it by annotating `MemberAdapter`; that reintroduces exactly the mistake removed from `MessageAdapter` on this branch. Longer term, setting `spring.jpa.open-in-view=false` will expose every other place quietly relying on it.

---

## Medium

### 3. `ChannelAdapter.findById` loads the entire channel graph to set one FK
**File:** `adapters/out/persistence/channel/ChannelAdapter.java:29-31`

```java
public Optional<Channel> findById(Integer id) {
  return repo.findById(id).map(mapper::toDomain);
}
```

`ChannelMapper.toDomain` walks every lazy association on `ChannelEntity` — `creator` and `leader` (`@ManyToOne(LAZY)`, lines 42-48) and `members` (`@OneToMany`, lines 50-51) — then dereferences `MemberEntity.user` for each member.

One `findById` therefore costs: channel SELECT + creator + leader + members + one user SELECT per member. For a 50-member channel that is roughly 53 queries.

> **Update (2026-09-21): the hot path is resolved, the finding is not.** `AbstractMessageCreationStrategy` no longer calls `findById` — it uses `channelPort.existById` and lets the adapter resolve the FK with `em.getReference`, so message creation no longer triggers this walk. The finding stays open because `WsSubscribeService.subscribe` and `ChannelService.updateChannel` still call `ChannelAdapter.findById` and still pay the full cost.

This used to fire on **every single message save**, via `AbstractMessageCreationStrategy.createMessage`, purely to set a foreign key. The surrounding transaction keeps the session open, so the cost shows up as latency rather than an error — which is why it went unnoticed.

**Fix:** same principle as §3 in `2026-09-19-list-messages-improvements.md`, which covers the `User` load but not the `Channel` load: do not hydrate an aggregate to set an FK. Either check existence and use `getReferenceById`, or give the write path a projection loading only the needed columns. Where the full graph is genuinely needed, add an explicit `JOIN FETCH` query rather than relying on lazy walks.

**Related risk, same mapper:** `ChannelAdapter.update` (lines 72-77) maps a domain `Channel` to a detached entity and calls `repo.save`. Because `members` is `cascade = ALL, orphanRemoval = true`, merging an entity whose members list is incomplete will **delete** the missing member rows. `ChannelService.updateChannel` is also not transactional. Verify this before the next change to that path.
