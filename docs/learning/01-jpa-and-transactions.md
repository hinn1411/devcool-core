# 01 — JPA & Transactions

The theme: **you cannot see where your code talks to the database.** Every finding here is a query, a write, or a transaction boundary that isn't visible at the call site.

---

## 1. A mapper is executable code that walks the object graph

**The concept.** A lazy association is a proxy. Touching any getter on it — even in code that looks like pure data copying — issues SQL. MapStruct generates a method that calls *every* getter on the source object. So "mapping an entity to a domain model" is not free data movement; it is a graph traversal, and each edge is a round trip.

**In your code** — `adapters/out/persistence/channel/ChannelAdapter.java:28-31`:

```java
@Override
public Optional<Channel> findById(Integer id) {
  return repo.findById(id).map(mapper::toDomain);
}
```

Three lines. Now look at what `ChannelEntity` declares (`ChannelEntity.java:42-51`):

```java
@ManyToOne(fetch = FetchType.LAZY) private UserEntity creator;
@ManyToOne(fetch = FetchType.LAZY) private UserEntity leader;
@OneToMany(mappedBy = "channel", cascade = CascadeType.ALL, orphanRemoval = true)
private List<MemberEntity> members = new ArrayList<>();
```

The generated `channelEntityToChannel` reads all three, then `memberEntityToMember` reads `MemberEntity.user` for each member. One `findById` costs:

```
SELECT channel          1
SELECT creator          1
SELECT leader           1
SELECT members          1
SELECT user × N members N
                     ────
                    4 + N
```

**Why it matters.** Until the `senderId` refactor, this ran on **every chat message** just to set a foreign key — about 53 queries on a 50-member channel. It still runs on `WsSubscribeService.subscribe` and `ChannelService.updateChannel`.

Worse, in `WsSubscribeService:31` the result is assigned and **never read**:

```java
Channel channel =
    channelPort
        .findById(command.channelId())
        .orElseThrow(...);
```

53 queries to discover a row exists. SpotBugs already reports this as a dead store (`DLS_DEAD_LOCAL_STORE`, line 34) and nobody saw it — see chapter 06.

**The fix.** Ask what the caller needs. An existence check is `EXISTS`:

```java
if (!channelPort.existById(channelId)) {
  throw new ChannelNotFoundException(channelId);
}
```

`ChannelPort.existById` already exists (`ChannelPort.java:12`) and is already used correctly by `MessageService.getMessages:62`.

**Verify it yourself.**
```properties
# application-local.properties, temporarily
logging.level.org.hibernate.SQL=DEBUG
```
Send one WebSocket `SUBSCRIBE` and count the statements. If you see per-member `SELECT`s, the mapper is doing the walking.

**Where else this applies.** `MemberAdapter.findMemberOfChannelByUserId:33-36` has exactly the same shape — it loads a full `MemberEntity`, the mapper dereferences `MemberEntity.user` (LAZY), and all three call sites use only `.isEmpty()`. That's two queries and a full user row, including the password hash, to compute one boolean, **on every message send**. A derived `boolean existsByChannel_IdAndUser_Id(...)` is one index-only query.

---

## 2. JPA defaults are not neutral

Every default below is (a) different from what you'd guess and (b) wrong for this codebase.

### `@ManyToOne` and `@OneToOne` default to **EAGER**

Only `@OneToMany`/`@ManyToMany` default to LAZY. You wrote `fetch = FetchType.LAZY` explicitly on the `@ManyToOne`s in `ChannelEntity`, `MemberEntity` and `MessageEntity` — so you know this. But `MediaEntity.java:26-28` has no `fetch`:

```java
@OneToOne
@JoinColumn(name = "MESSAGE_ID", referencedColumnName = "ID")
private MessageEntity message;
```

Any `mediaRepository.findById(...)` drags in the whole message. And `FriendRequestEntity.java:29-39` / `AuthProviderEntity.java:30-32` declare `fetch = FetchType.EAGER` *deliberately*. EAGER cannot be overridden per query — JPQL `JOIN FETCH` can add fetches, never remove them — so `SELECT f FROM FriendRequestEntity f` is a guaranteed 2N+1. **Map everything LAZY; opt into fetching per use case.**

### `@Enumerated` defaults to **ORDINAL**

`AuthProviderEntity.java:21-22`:
```java
@Column(name = "PROVIDER", nullable = false)
private LoginType provider;
```

No `@Enumerated` → stored as `0`, `1`. Insert a constant into the middle of `LoginType` and every existing row silently changes meaning. There is no migration that can detect this, because the column is just an integer.

You have `@Enumerated(EnumType.STRING)` correctly on six other columns. That's what makes this instructive: the rule is being applied **by habit, not by knowing the default**.

### `List` on `@OneToMany` is a **bag**, not an ordered list

`ChannelEntity.java:50-51` — a `List` with no `@OrderColumn` is an unordered bag. With `orphanRemoval = true`, removing one element makes Hibernate `DELETE` the association rows and re-`INSERT` the survivors instead of one targeted `DELETE`. `Set` is the correct default — which requires §4.

### `GenerationType.SEQUENCE` defaults to `allocationSize = 50`

Seven entities use `@GeneratedValue(strategy = GenerationType.SEQUENCE)` with no `@SequenceGenerator`. Hibernate reserves blocks of 50. Its own generated DDL emits `INCREMENT BY 50`, so local is consistent — but since there are no migrations (§6), whoever creates the production schema by hand will write `CREATE SEQUENCE channel_seq;`, which is `INCREMENT BY 1`. Hibernate then hands out 50 ids it doesn't own → intermittent `duplicate key` violations, **only in production, only under load**, with `ddl-auto=validate` reporting the schema as valid (it does not check sequence increments).

### `spring.jpa.open-in-view` defaults to **true**

It is set nowhere in your three properties files. It is the only reason §1's lazy walks don't throw `LazyInitializationException` — OSIV holds the `EntityManager` open for the whole HTTP request, well past the transaction that loaded the entity.

Two consequences. First, setting `open-in-view=false` (the conventional production hardening) breaks `ChannelAdapter.findById`, both `MemberAdapter` finders and the `AuthAdapter` loaders all at once. Second, **the WebSocket path is not an MVC request**, so OSIV never applied there — it survives only because `WsSubscribeService` carries `@Transactional`. That annotation looks redundant and is load-bearing.

**Where this all applies.** Before using a JPA annotation, look up its default rather than assuming the sensible one. The pattern across all five: you know the *correct* value and apply it where you're thinking about it, and the default silently wins everywhere else.

---

## 3. `repository.save(detachedEntity)` is `merge` — it writes every column

**The concept.** `save()` on an entity with a non-null id is a `merge`: Hibernate SELECTs the current row, copies **all** fields from your detached instance over it, and UPDATEs every column. It is not a partial update. Any field that is stale in your in-memory copy overwrites the database.

**In your code** — `application/service/AuthenticateUserService.java:53-56`:

```java
private void updateLoginTime(User user) {
  user.updateLoginTime();
  userPort.save(user);
}
```

Intent: write one timestamp. Effect: rewrite every column of that user from a snapshot read at the start of the request — including `TOKEN_VERSION`.

**Why it matters.** If a logout's `token_version = token_version + 1` commits between this request's read and its write, the merge **overwrites the incremented version with the stale one**, resurrecting a session that was just revoked. There is no `@Version` anywhere in the codebase to detect it, so it is a silent last-write-wins.

There's a second bug stacked on the same line. `User.emailVerified` is a primitive `boolean`; `UserEntity.emailVerified` is a `Boolean`. The generated mapper null-guards the *read* but not the *write*. `register` never sets it, so the column is `NULL`; login reads `NULL` → the primitive stays `false` → save writes `false`. `NULL` ("unknown") silently becomes `false` ("checked and not verified").

**The fix.** Targeted update for a single field — the pattern you already use correctly in `UserRepository.updateTokenVersion`:

```java
@Modifying
@Query("update UserEntity u set u.lastLoginTime = :t where u.id = :id")
int updateLoginTime(@Param("id") Integer id, @Param("t") Instant t);
```

And make nullable columns map to wrapper types, per `lessons.md` #4 — which stated the rule for DTOs; it applies equally to the entity↔domain boundary.

**Where else this applies.** `ChannelAdapter.update:71-77` merges a detached `ChannelEntity` whose `members` collection has `cascade = ALL, orphanRemoval = true`. If that collection is incomplete, the merge **deletes** the missing member rows. Any place you read an aggregate, mutate one field and save it back.

---

## 4. Entities have three identities, and you've defined none of them

There is no `equals`/`hashCode` on any entity or domain model in the codebase (verified by grep across `domain/**` and `**/entity/*`).

A JPA entity has a Java reference identity, a database primary key, and a business key. They diverge across detach, merge and proxying — the same row loaded twice can be two objects, and an unsaved entity has a `null` id. Without `equals`, Hibernate's collection diffing treats every incoming member as new, which is what makes §3's orphan-removal hazard fire.

**The fix.** A stable business key (`channelId` + `userId` for `MemberEntity`), or a UUID assigned in the constructor. Never the generated `@Id` — it is null before flush.

---

## 5. The transaction boundary belongs to the use case

**The concept.** A transaction should wrap exactly one business operation. Only the application service knows where that starts and ends. An outbound adapter sees one port call and cannot know whether it is the whole operation or one step of five.

**In your code.** `@Transactional` currently lives on adapter methods — `AuthAdapter` (5), `UserAdapter` (8), and `ChannelAdapter.increaseTotalMembers` — while `ChannelService`, `AuthenticateUserService`, `RefreshTokenService` and `WsSendMessageService` have **none at all**.

That inverts the default: every individual write is atomic, and no multi-step use case is.

`application/service/AuthenticateUserService.java:31-56` does three writes with no enclosing transaction:

```java
updateLoginTime(user);                        // tx 1, commits
...
refreshStore.deleteOldRefreshTokens(user.getId());  // tx 2, commits
refreshStore.store(refreshToken);                   // tx 3
```

If `store` fails, the old refresh tokens are **already committed as deleted** and the new one was never written. The user is silently logged out of every device, holding a refresh token with no database row.

`ChannelService.addMember:95-96` has the same shape:

```java
channelPort.increaseTotalMembers(channelId, command.userIds().size());  // @Transactional on the adapter
return memberPort.addMembers(channelId, existingUserIds);               // no transaction anywhere
```

If the second fails, `TOTAL_OF_MEMBERS` is permanently inflated with no rows to match.

**The fix.** `@Transactional` on the service method; adapters stay transaction-agnostic and join the caller's transaction. This is already documented in `lessons.md` #17 and was applied to `MessageAdapter` — the rest of the codebase hasn't caught up. Full reasoning in `docs/improvements/2026-09-21-channel-transaction-and-query-issues.md`.

**One extra trap.** `@Transactional` only rolls back the *database*. `WsSubscribeService:52` mutates an in-memory registry inside a transaction — if the transaction later rolls back, the subscription persists. Non-transactional side effects (caches, message emission, HTTP calls) belong **after commit**, via `@TransactionalEventListener(phase = AFTER_COMMIT)`. That same mechanism is the correct fix for broadcasting a message only once it is durably stored (chapter 04).

---

## 6. The schema is code

**The concept.** `ddl-auto` generates a schema as a side effect of your entity classes. A migration tool records the schema as a reviewed, versioned, ordered artifact. Only the second is reproducible.

**In your code.**

- `pom.xml:73` declares `flyway-core`
- `application-local.properties:14` → `spring.flyway.enabled=false`
- `application-ecs.properties:10` → `spring.flyway.enabled=false`
- `find src/main/resources -name "V*__*.sql"` → **nothing**
- `application-ecs.properties:7` → `spring.jpa.hibernate.ddl-auto=validate`
- `application-local.properties:11` → `spring.jpa.hibernate.ddl-auto=update`

**Why it matters.** Production validates a schema that **nothing in this repository can create**. Against a fresh RDS instance the app fails at startup with `SchemaManagementException`. If it currently works, the schema exists only in someone's shell history — unreviewed, unversioned, unreproducible.

Locally, `update` is additive-only: it never drops, narrows or renames. The `SENDER_USER_ID` → `USER_ID` rename in `lessons.md` #11 left the old `NOT NULL` column in place. Your local schema is a function of *the entire history* of your entity classes, not their current state — so two developers with different commit histories have different databases from identical code.

`README.md` advertises Flyway as the migration tool. `CLAUDE.md:141` says local uses `create-drop`; it uses `update`.

**The fix.** Generate the current schema, commit it as `V1__baseline.sql`, enable Flyway, set `ddl-auto=validate` everywhere including local. Then local and production are produced by the same mechanism and "works on my machine" becomes meaningful.

Also move `spring.jpa.generate-ddl=true` and `spring.jpa.show-sql=true` out of `application.properties` — that file applies to **every** profile, so production currently logs every statement.

---

## 7. A pre-flight `exists` check is not a constraint

`UserService.register:54-67` checks `existsByUsername` then `existsByEmail` then saves. `ChannelService.addMember` checks `findMembersOfChannelByUserIds` then inserts. Both are check-then-act across a window where another request can interleave.

The unique constraints do exist (`UserEntity.java:11-17`, `MemberEntity`'s `uk_member_channel_user`), so the data cannot be corrupted — good. But there is **no `@ExceptionHandler(DataIntegrityViolationException.class)`** in `ApiExceptionHandler`, so the loser of the race gets a **500** while the winner's path returns a clean 409. Two identical requests, two different status codes, decided by microseconds.

**The rule.** The pre-flight check is a UX nicety. The database constraint is the correctness mechanism — so the constraint violation must be handled as a **normal, expected outcome**, not an unexpected error.

---

## Where else this applies — a checklist

- [ ] Every mapper that takes an entity: which lazy associations does it touch?
- [ ] Every `findById` whose result is used only for existence → `existsBy`
- [ ] Every `@ManyToOne`/`@OneToOne` has an explicit `fetch = LAZY`
- [ ] Every enum column has `@Enumerated(EnumType.STRING)`
- [ ] Every multi-write service method has `@Transactional`; no adapter does
- [ ] Every `save(entity)` that means "update one field" → `@Modifying` query
- [ ] Every nullable column maps to a wrapper type
- [ ] Every unique constraint has a matching exception handler
- [ ] `open-in-view` is set explicitly, not inherited
