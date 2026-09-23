# 02 — Security & Authorization

Read this one first. Several items here are live.

---

## 1. A domain object returned from a controller is a public API

**The concept.** Jackson serialises whatever you hand it, using every public getter. A domain aggregate is designed for *internal* completeness — it holds everything the business logic might need. A response DTO is designed for *external* narrowness — it holds only what the caller should see. The moment you return the first one, every field you ever add to the domain becomes public API, retroactively and silently.

**In your code** — `adapters/in/web/controller/UserController.java:24-31`:

```java
@GetMapping("/{id}")
public ResponseEntity<ApiSuccessResponse<User>> getProfile(@PathVariable Integer id) {
  User user = getUser.byId(id);
  return ResponseEntity.ok(
      ApiResponseFactory.success(
          HttpStatus.OK, ErrorCode.OK.code(), "Get profile successfully", user));
}
```

`domain/user/model/User.java:11-25` is `@Getter` over:

```java
private String username;
private String password;   // store hashed password
private String email;
private Role role;
private UserStatus status;
private Integer tokenVersion;
```

No `@JsonIgnore` anywhere. `/api/v1/users/**` is not in the `permitAll` list, so it falls to `anyRequest().authenticated()` — meaning **any** valid token, not the *owning* token.

**Why it matters.** Two failures at once. An authenticated attacker iterates `/api/v1/users/1`, `/2`, `/3`… and collects every bcrypt hash in the system for offline cracking, plus every email address. There is also no ownership check, so this is an IDOR independent of the hash leak.

**The fix.** You already built the right thing one endpoint away — `AuthController.getProfile:163` maps through `AuthDtoMapper` to `GetProfileResponse`. Use it here, drop `password` and `tokenVersion` from the response entirely, and add an ownership check (or delete the endpoint; `/auth/profile` already covers the legitimate use).

**Verify it yourself.**
```bash
curl -s -H "Authorization: Bearer $TOKEN" localhost:8080/api/v1/users/1 | jq '.data.password'
# Any non-null output is the bug.

# Structural check you can run in CI:
grep -rn "ApiSuccessResponse<User>\|ApiSuccessResponse<Channel>\|ApiSuccessResponse<Message>" \
  src/main/java/com/devcool/adapters/in/web/controller/
```

**Where else this applies.** Any controller whose generic parameter is a `com.devcool.domain` type. `lessons.md` #13 covers the DTO side of this; this is the same rule applied to the *response envelope*, which that lesson didn't reach.

---

## 2. Exception details are a public data channel

**The concept.** A structured error payload is part of your API contract. Anything you put in it is serialised to the client, written to access logs that capture bodies, and shipped to whatever APM or error-tracking service you add later. It is not a debugging side-channel visible only to you.

**In your code** — `domain/auth/exception/PasswordIncorrectException.java:8-10`:

```java
public PasswordIncorrectException(String password) {
  super(ErrorCode.PASSWORD_INCORRECT, "Password is incorrect", Map.of("password", password));
}
```

thrown at `application/service/AuthenticateUserService.java:38-41`:

```java
if (!hasher.matches(command.password(), user.getPassword())) {
  log.warn("Password does not match!");
  throw new PasswordIncorrectException(command.password());
}
```

and serialised at `adapters/in/web/handler/ApiExceptionHandler.java:22-28`:

```java
return ResponseEntity.status(status)
    .body(ApiResponseFactory.error(
        status, ex.getErrorCode().code(), ex.getMessage(), ex.getDetails()));
```

**Why it matters.** `POST /api/v1/auth/login` with a wrong password returns the plaintext password the user just typed. Because `PASSWORD_INCORRECT` is also unmapped in `HttpErrorMapper` (chapter 05), it arrives as a **500** — so it lands in error-tracking with the credential attached.

**The fix.** Never pass a credential to an exception. `details` should describe *which field* was wrong, never *what value* was sent:

```java
throw new PasswordIncorrectException();   // no argument at all
```

**Verify it yourself.**
```bash
grep -rn 'Map.of("password"' src/main/java/com/devcool/domain/
```

**Where else this applies.** Audit every `Map.of(...)` in `domain/**/exception/`. Today the others carry ids and usernames — acceptable, though `UserNotFoundException(username)` feeds the enumeration problem in §4. The rule: details carry *identifiers and constraints*, never *submitted secrets*.

---

## 3. Revocation must actually revoke

**The concept.** A token-version counter is a generation marker. A token minted at generation 3 is valid **only** while the current generation is 3. Bumping the stored counter is what kills it. That makes the comparison an **equality** test — "was this minted at the current generation?" — not an ordering test.

**In your code** — `domain/user/model/User.java:40-42`:

```java
public boolean isTokenVersionValid(Integer currentVersion) {
  return tokenVersion >= currentVersion;
}
```

`tokenVersion` is the stored value; `currentVersion` is the claim from the presented token (`JwtAuthFilter.java:49-51`). Logout runs `token_version = token_version + 1`. So: DB becomes 4, the old token still claims 3, and `4 >= 3` is **true**. The old token is accepted.

It gets worse. `adapters/out/jwt/util/JwtUtils.java:45-53`:

```java
public static Integer versionFrom(String token) {
  JWTClaimsSet claims = getClaims(token);
  try {
    return claims.getIntegerClaim("version");
  } catch (ParseException e) {
    log.warn("Cannot get version from token!");
    return -1;
  }
}
```

A `-1` sentinel means `anything >= -1` is always true. A token whose `version` claim is malformed passes unconditionally. And if the claim is merely *absent*, `getIntegerClaim` returns `null` rather than throwing, so `tokenVersion >= null` throws NPE on unboxing → 500.

**Why it matters.** Logout is decorative. Every access token issued before a logout remains valid for its full lifetime — and `TokenIssuerAdapter.java:35` sets that to `3600` seconds with the comment `// 15 min`. So a "logged out" session stays live for an hour. Password change doesn't bump the version either (§5), so a compromised password cannot be contained.

**The fix.**
```java
public boolean isTokenVersionValid(Integer currentVersion) {
  return Objects.equals(tokenVersion, currentVersion);
}
```
Make `versionFrom` return `Optional<Integer>` (or throw) instead of `-1`, and fix the TTL constant or its comment — they cannot both be right.

**Verify it yourself.** This is three assertions and needs no infrastructure:
```java
@Test void tokenFromPreviousGenerationIsRejected() {
  User u = User.builder().tokenVersion(4).build();
  assertThat(u.isTokenVersionValid(3)).isFalse();   // fails today
}
```

**Where else this applies.** Any directional predicate — `>=` vs `<=` vs `==` — in a security decision. Code review does not catch these, and manual testing never exercises them, because the failing case is the one you don't retry by hand. They need a unit test, always. A sentinel return value (`-1`, `null`, `""`) from a *verification* function is the second half of the same lesson: ask "what does this value mean to my caller?" before choosing it.

---

## 4. Authorization belongs where privilege is *granted*, not only where it is used

**The concept.** Access control is a graph, not a checklist. If three endpoints correctly ask "is this user a member of this channel?", they all depend on membership being hard to obtain. The endpoint that *creates* membership is therefore the most security-critical one in the set — and it is the easiest to overlook, because it doesn't look like a read.

**In your code** — `adapters/in/web/controller/ChannelController.java:66-75`:

```java
@PostMapping("/{channelId}/members")
ResponseEntity<ApiSuccessResponse<AddMembersResponse>> addMembers(
    @Valid @RequestBody AddMembersRequest request, @PathVariable Integer channelId) {
  AddMembersCommand command = mapper.toAddMembersCommand(request);
  boolean isMemberAdded = channelUpdater.addMember(channelId, command);
```

No `Authentication` parameter — the method literally cannot know who is calling. `ChannelService.addMember:70-97` validates duplicates, user existence, channel existence and prior membership, but never *who is asking*.

Same at `ChannelController.java:55-64` (`PATCH /{channelId}`), backed by `ChannelService.updateChannel:61-67`, which checks only that the channel exists.

**Why it matters.** Any authenticated user posts `{"userIds":[<their own id>]}` against any `channelId` and becomes a member. That single gap then unlocks three checks that are written *correctly*:

- `MessageService.getMessages:65` — read all messages
- `WsSubscribeService:40` — subscribe to the live feed
- `AbstractMessageCreationStrategy.requireMemberInChannel` — post messages

This is one privilege-escalation chain, not three bugs. Private chats are readable by anyone with an account.

**The fix.** Take `Authentication` in both handlers, pass the caller's id into the command, and enforce in the service (where the invariant lives, per `lessons.md` #6):

```java
// in ChannelService.addMember, before any mutation
if (memberPort.findMemberOfChannelByUserId(channelId, callerId).isEmpty()) {
  throw new MemberNotFoundException(callerId);
}
// and for a role-gated operation, check the caller's MemberType
```

**Verify it yourself.**
```bash
# Every handler that mutates a {id}-scoped resource should take Authentication.
grep -rn -A3 "@PatchMapping\|@PostMapping\|@DeleteMapping\|@PutMapping" \
  src/main/java/com/devcool/adapters/in/web/controller/ | grep -B1 "@PathVariable"
```
Then check each hit takes an `Authentication` (or `@AuthenticationPrincipal`) parameter.

**Where else this applies.** `lessons.md` #6 says "authenticated ≠ authorized" and #9 extends it to URL-issuing endpoints. This adds the third and most important case: **the grant point**. When you add a check, immediately ask "what creates the thing I'm checking for, and is *that* guarded?"

---

## 5. An unimplemented security endpoint must fail closed

**The concept.** Returning `null` from a `@RestController` method tells Spring the response has already been handled. Spring commits an empty **200 OK**. There is no warning — the signature promises a body and the framework quietly ships none.

**In your code** — `adapters/in/web/controller/AuthController.java:140-144`:

```java
@PostMapping("/password")
public ResponseEntity<ApiSuccessResponse<Boolean>> changePassword(
    @Valid @RequestBody ChangePasswordRequest request) {
  return null;
}
```

The layer beneath is also a stub — `adapters/out/persistence/user/UserAdapter.java:60-64`:

```java
@Override
@Transactional
public boolean updatePassword(Integer id, String newHash) {
  return false;
}
```

And the use case between them is fully written: `UserService.change:30-40` loads the user, verifies the current password with BCrypt, hashes the new one — then hands it to the stub, which discards it.

**Why it matters.** A user changes their password, sees success, and their password is unchanged. They believe a compromised credential has been rotated. Note also there's no `Authentication` parameter, so even once implemented there's no identity to scope the change to — and no `tokenVersion` bump, so existing sessions would survive a password change anyway.

**The fix.** Unimplemented port methods throw:

```java
@Override
public boolean updatePassword(Integer id, String newHash) {
  throw new UnsupportedOperationException("not implemented");
}
```

A stub that returns `false` is indistinguishable from a legitimate "no rows affected", so the failure is invisible at every layer above. `boolean` as a command return type invites this — prefer `void` + throw.

**Where else this applies.** `UserService.byEmail:48-52` returns `Optional.empty()` as a stub. That's worse in a subtle way: `Optional.empty()` means *"I looked and found nothing."* Using it for *"I didn't look"* destroys the only information `Optional` carries, and no test can distinguish them.

---

## 6. Parsing a JWT is not verifying it

**The concept.** `SignedJWT.parse(token)` base64-decodes. That's all. Signature verification is a separate call. Any claim read from a parsed-but-unverified token is attacker-controlled input.

**In your code** — `adapters/out/jwt/util/JwtUtils.java:55-62`:

```java
private static JWTClaimsSet getClaims(String token) {
  try {
    return SignedJWT.parse(token).getJWTClaimsSet();
  } catch (Exception e) {
    log.warn("Invalid JWT token!");
    throw new IllegalArgumentException("Invalid JWT token", e);
  }
}
```

`jtiFrom`, `subjectFrom`, `roleFrom` and `versionFrom` all route through it. `RefreshTokenService.revokeRefreshToken:58-69` then acts on the result:

```java
String jti = JwtUtils.jtiFrom(refreshToken);
String hashJti = HashUtils.sha256(jti);
if (!refreshStore.revoke(hashJti)) {
  log.warn("Cannot revoke token!");
}
```

and `/api/v1/auth/logout` is in the `permitAll` list (`SecurityConfig.java:40`), with `revokeRefreshToken` running *before* `auth.getName()` in the handler.

**Why it matters.** An unauthenticated caller who learns a victim's `jti` can forge a JWT with any signature and revoke that victim's session. The names are the trap: `jtiFrom` reads exactly like a safe accessor.

**The fix.** Verify first, then read claims — and name unverified accessors so they cannot be used by accident (`unsafeJtiFromUnverifiedToken`). In `JwtAuthFilter` the ordering happens to be safe (`isAccessTokenValid` runs first at line 41), but that safety is positional, not structural.

**Where else this applies.** Any helper whose name implies validation it doesn't perform. `getClaims` throwing `IllegalArgumentException` — not a `DomainException` — also means it lands in the catch-all as a 500 rather than a 401 (chapter 05).

---

## 7. `permitAll` is an allowlist — every entry needs a reason

**In your code** — `adapters/in/web/config/SecurityConfig.java:34-45`:

```java
.requestMatchers(
    "/api/v1/auth/register",
    "/api/v1/auth/login",
    "/api/v1/auth/refresh_token",
    "/api/v1/auth/logout",
    "/public/**",
    "/error",
    "/ws",
    "/api/v1/channels")
.permitAll()
```

`POST /api/v1/channels` (create) and `GET /api/v1/channels` (list) are unauthenticated. The only thing stopping an anonymous caller is `ChannelController.java:42`:

```java
Integer userId = Integer.valueOf(auth.getName());
```

For an anonymous request Spring supplies an `AnonymousAuthenticationToken` whose `getName()` is `"anonymousUser"` → `NumberFormatException` → **500**. Access control "works" by crashing.

**The fix.** Remove `/api/v1/channels` and `/api/v1/auth/logout` from the list. Logout needs a principal; it shouldn't be public.

**Where else this applies.** `lessons.md` #5 warns about leftover debug defaults. This is that pattern in the security config: entries added to make a 403 go away during manual testing. Every `permitAll` entry should be justifiable in one sentence.

---

## 8. Authentication failures must be indistinguishable

`AuthenticateUserService.login` throws `UserNotFoundException` when the username is unknown (→ **404**, `details.userName`) and `PasswordIncorrectException` when the password is wrong (→ **500**, `details.password`). Different status, different body, and different timing — BCrypt only runs when the user exists, so the delta is tens of milliseconds and trivially measurable.

Any of those three channels lets an attacker enumerate valid accounts.

**The fix.** One exception, one status, for both: `401 "Invalid credentials"`. To close the timing channel, compare against a dummy hash when the user is absent so both paths pay the same cost.

---

## 9. Cookie `Path` is segment-matching, not `startsWith`

`AuthController.java:129` scopes the refresh cookie:

```java
.path("/api/v1/auth/refresh")
```

The endpoint is `@PostMapping("/refresh_token")` → `/api/v1/auth/refresh_token`.

RFC 6265 §5.1.4: the cookie is sent when the request path equals the cookie path, or the cookie path ends in `/`, or **the first character after the cookie path is `/`**. Here it is `_`. The browser never sends the cookie. `@CookieValue("rt")` is required → `MissingRequestCookieException` → 500.

The same wrong path appears at lines 182 and 206, so logout and the clearing cookie are broken too.

Separately, `AuthDtoMapper.toLoginResponse:20-25` also returns the refresh token **in the JSON body** — which defeats the entire point of `httpOnly(true)`. Cookie-versus-body is an either/or decision; supporting both means any XSS reads the token.

**Why this is really in here.** Narrow cookie scoping is *good practice* — you did the right thing and got the string wrong. The lesson isn't about cookies. It's that a login→refresh integration test would have caught it in seconds, and there isn't one. See chapter 06.

---

## Checklist

- [ ] No controller returns a `com.devcool.domain` type
- [ ] No exception `details` map contains a credential
- [ ] Revocation comparisons use `equals`, not `>=`
- [ ] Every `{id}`-scoped mutation takes `Authentication` and checks it in the service
- [ ] The endpoint that *grants* a privilege is guarded at least as well as the ones that consume it
- [ ] Unimplemented methods throw; they never return a plausible default
- [ ] Claims are read only after signature verification
- [ ] Every `permitAll` entry has a one-sentence justification
- [ ] Login returns one indistinguishable failure for unknown-user and wrong-password
