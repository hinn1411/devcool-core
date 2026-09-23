# 09 — Classic vs Mockist, Shown on Real Code

The question this chapter answers: **what do the strengths and weaknesses of the two unit-testing schools actually look like when code changes?**

[07 §1](07-unit-testing-guide.md#1-what-a-unit-test-is) summarises the two schools in a table:

| School | Replaces with doubles… | Strength | Weakness |
|---|---|---|---|
| **Classic (Detroit)** | Only what's slow, non-deterministic or external | Survives refactoring ([A](#a-a-refactor-classic-survives-mockist-breaks)); catches bugs between your own classes ([B](#b-an-integration-bug-classic-catches-it-mockist-hides-it)) | A failure can point at several classes ([C](#c-a-broken-line-mockist-pinpoints-it-classic-spreads-it)) |
| **Mockist (London)** | Every collaborator | Pinpoints the failing class ([C](#c-a-broken-line-mockist-pinpoints-it-classic-spreads-it)); forces you to design interfaces ([D](#d-design-pressure-mockist-makes-you-decide-the-contract)) | Couples to *how* the code works ([A](#a-a-refactor-classic-survives-mockist-breaks)) |

This chapter makes each cell concrete.

The same behaviour is tested both ways, and then something happens to the code: a refactor, a bug, a broken line, a design decision. Watch which tests react.

**The behaviour under test** is posting a message. `MessageService.save` picks the strategy for the content type. `TextMessageCreationStrategy` checks that the sender is a member of the channel, then saves the message.

```
MessageService ──► TextMessageCreationStrategy ──► ChannelPort / MemberPort / MessagePort
   (yours)              (yours)                         (ports: the outside world)
```

**The spec:**
- a member can post, and the message is saved
- a non-member gets `MemberNotFoundException`, and nothing is saved

## The mockist test

Every collaborator is a mock, including your own strategy. The strategy's tests stub and verify each port call it makes. This is written against the code **before** this branch's refactor, where the strategy loaded the full `Channel` and `User` to build the message (`git show HEAD:src/main/java/com/devcool/application/service/chat/strategy/AbstractMessageCreationStrategy.java`):

```java
// MessageServiceTest: the strategy is a mock
@Test void save_delegatesToStrategyForContentType() {
  when(textStrategy.getSupportedType()).thenReturn(ContentType.TEXT);
  when(textStrategy.createMessage(cmd)).thenReturn(55);
  var service = new MessageService(List.of(textStrategy), messagePort, channelPort, memberPort);

  assertThat(service.save(cmd)).isEqualTo(55);
  verify(textStrategy).createMessage(cmd);
}

// TextMessageCreationStrategyTest: every port is a mock, every call is scripted
@Test void createMessage_member_savesMessage() {
  when(memberPort.findMemberOfChannelByUserId(5, 7)).thenReturn(Optional.of(member));
  when(channelPort.findById(5)).thenReturn(Optional.of(channel));
  when(userPort.loadById(7)).thenReturn(Optional.of(user));
  when(messagePort.save(any())).thenReturn(55);

  assertThat(strategy.createMessage(cmd)).isEqualTo(55);
  verify(channelPort).findById(5);
  verify(userPort).loadById(7);
}

@Test void createMessage_nonMember_throws() {
  when(memberPort.findMemberOfChannelByUserId(5, 99)).thenReturn(Optional.empty());

  assertThatThrownBy(() -> strategy.createMessage(nonMemberCmd))
      .isInstanceOf(MemberNotFoundException.class);
  verifyNoInteractions(messagePort);
}
```

## The classic test

Your own classes are real: the real `MessageService` wrapping the real `TextMessageCreationStrategy`. Only the ports, which stand for the database, are replaced, and they're replaced by a **fake**: a tiny in-memory "database" with no scripted answers.

```java
/** One in-memory fake standing in for the database behind all three ports. */
class InMemoryChatStore implements ChannelPort, MemberPort, MessagePort, LoadUserPort {
  final Set<Integer> channels = new HashSet<>();
  final Set<Integer> users = new HashSet<>();
  final Map<Integer, Set<Integer>> membersByChannel = new HashMap<>();
  final List<Message> savedMessages = new ArrayList<>();

  void join(int channelId, int userId) {
    users.add(userId);
    membersByChannel.computeIfAbsent(channelId, k -> new HashSet<>()).add(userId);
  }

  @Override public boolean existById(Integer id) { return channels.contains(id); }
  @Override public Optional<Channel> findById(Integer id) {
    return existById(id) ? Optional.of(Channel.builder().id(id).build()) : Optional.empty();
  }
  @Override public Optional<User> loadById(Integer id) {
    return users.contains(id) ? Optional.of(User.builder().id(id).build()) : Optional.empty();
  }
  @Override public Optional<Member> findMemberOfChannelByUserId(Integer channelId, Integer userId) {
    return membersByChannel.getOrDefault(channelId, Set.of()).contains(userId)
        ? Optional.of(Member.builder().user(User.builder().id(userId).build()).build())
        : Optional.empty();
  }
  @Override public Integer save(Message message) {
    savedMessages.add(message);
    return savedMessages.size();
  }
  // every other port method: throw new UnsupportedOperationException();
}
```

```java
class PostMessageTest {
  InMemoryChatStore db = new InMemoryChatStore();
  MessageService service;

  @BeforeEach void setUp() {
    db.channels.add(5);
    db.join(5, 7);
    var text = new TextMessageCreationStrategy(db, db, db, db);   // real strategy
    service = new MessageService(List.of(text), db, db, db);      // real service
  }

  @Test void member_canPost() {
    service.save(new CreateMessageCommand("hi", ContentType.TEXT, 5, 7));

    assertThat(db.savedMessages).singleElement()
        .extracting(Message::getContent).isEqualTo("hi");
  }

  @Test void nonMember_cannotPost() {
    assertThatThrownBy(() -> service.save(new CreateMessageCommand("hi", ContentType.TEXT, 5, 99)))
        .isInstanceOf(MemberNotFoundException.class);
    assertThat(db.savedMessages).isEmpty();
  }
}
```

The classic test never says *which* port methods get called. It sets up a world ("channel 5 exists, user 7 is in it"), acts, and checks the outcome.

---

## A. A refactor: classic survives, mockist breaks

Here is what this branch actually did to the strategies (`git diff HEAD -- src/main/java/com/devcool/application/service/chat/strategy/`):

| Before | After |
|---|---|
| Each subclass overrode `createMessage` and checked membership itself | One `final createMessage` in the abstract class does the checks |
| Loaded the channel with `channelPort.findById(...)` | Checks it with `channelPort.existById(...)` |
| Loaded the sender with `userPort.loadById(...)` | No user load; `LoadUserPort` removed from the constructor |
| Checked membership first, loaded the channel second | Checks the channel first, membership second |

**The spec didn't change.** Members can still post and non-members still can't.

Now re-run the tests.

**Mockist `createMessage_member_savesMessage`: red, for four reasons.** None of them is a bug.
- `userPort` no longer exists in the constructor → **compile error**
- `channelPort.findById(5)` is stubbed but never called → **`UnnecessaryStubbingException`** (strict stubs)
- `channelPort.existById(5)` is new and unstubbed, so the mock returns `false` → **`ChannelNotFoundException`**
- `verify(channelPort).findById(5)` → **"Wanted but not invoked"**

**Mockist `createMessage_nonMember_throws`: red.** The new code checks the channel *first*. `existById` is unstubbed, so it returns `false`, and the test gets `ChannelNotFoundException` instead of `MemberNotFoundException`. The test described correct behaviour, and it still failed.

**Classic `PostMessageTest`: both tests stay green.** The only edit is the constructor line in `setUp` (`new TextMessageCreationStrategy(db, db, db)`). The fake answers `existById` correctly because channel 5 really is "in the database". The test bodies don't change at all.

This is what "coupled to *how* the code works" means. The mockist test was a line-by-line script of the old implementation (`findById`, then `loadById`, then `save`). Any change to the script is a failure, even when the result is identical.

> **The middle ground.** Mockito stubs on the ports (the style this repo uses, and the one recommended here) break in *some* of these ways but not others. `existById` still needs a new stub, because a stub is a small script. But dropping `verify` on queries (07 §5) removes the "Wanted but not invoked" failures. A fake removes the rest. That's why 07 §6 says fakes are underrated.

One more thing the refactor did change: for a request where the channel is missing **and** the user isn't a member, the error changed from `MemberNotFoundException` to `ChannelNotFoundException`. That's a real behaviour change, deliberate and documented in the Javadoc. A classic test for that case *should* go red, and it would. **In a classic suite, red means the behaviour changed. In a mockist suite, red often only means the code changed.**

---

## B. An integration bug: classic catches it, mockist hides it

`WsSendMessageService` (`WsSendMessageService.java:32-43`) loops over the connections subscribed to a channel:

```java
Set<String> connections = connectionRegistryPort.getConnectionsByChannel(command.channelId());
...
for (String connectionId : connections) {
```

**Mockist test** (the registry is a mock):

```java
@Test void sendMessage_noSubscribers_savesAndSendsNothing() {
  // getConnectionsByChannel is not stubbed → Mockito returns an EMPTY Set
  service.sendMessage(cmd);

  verify(saveUseCase).save(any());
  verifyNoInteractions(emitter);
}
```
**Green.** The service looks correct.

**Classic test** (the real `InMemoryConnectionRegistryAdapter`):

```java
@Test void sendMessage_noSubscribers_savesAndSendsNothing() {
  var registry = new InMemoryConnectionRegistryAdapter();     // real, nobody subscribed
  var service = new WsSendMessageService(registry, emitter, saveUseCase);

  service.sendMessage(cmd);                                   // 💥 NullPointerException
  ...
}
```
**Red.** The real registry returns `null` for an unknown channel (`InMemoryConnectionRegistryAdapter.java:45-47`), and the `for` loop dereferences it.

Each class passes its own mockist tests. The bug lives **between** them, in a mismatch of assumptions: the service assumes "never null", and the registry delivers "null when empty". A mock can't find that, because the mock *is* the assumption. You wrote down what you believed the registry does, and the test checked your belief against itself.

In production this fires on the first message sent to a channel nobody has subscribed to. The message is already saved (`saveUseCase.save` ran first), the sender gets an error, and they retry. That produces a duplicate message.

---

## C. A broken line: mockist pinpoints it, classic spreads it

Suppose someone breaks one line in `AbstractMessageCreationStrategy.requireMemberInChannel` (`:55-58`):

```java
if (memberPort.findMemberOfChannelByUserId(channelId, userId).isPresent()) {   // was isEmpty()
  throw new MemberNotFoundException(userId);
}
```

**Mockist suite:** only the strategy's own tests go red.

```
[ERROR] TextMessageCreationStrategyTest.createMessage_member_savesMessage   » MemberNotFound
[ERROR] TextMessageCreationStrategyTest.createMessage_nonMember_throws      expected MemberNotFoundException
[ERROR] ImageMessageCreationStrategyTest.createMessage_member_savesMessage  » MemberNotFound
Tests run: 40, Failures: 3
```
`MessageServiceTest` and `WsSendMessageServiceTest` stay green, because they mock the strategy and the use case. The report points straight at the strategy.

**Classic suite:** every test whose path runs through that line goes red.

```
[ERROR] TextMessageCreationStrategyTest.member_canPost              » MemberNotFound
[ERROR] TextMessageCreationStrategyTest.nonMember_cannotPost        expected MemberNotFoundException
[ERROR] ImageMessageCreationStrategyTest.member_canPostImage        » MemberNotFound
[ERROR] PostMessageTest.member_canPost                              » MemberNotFound
[ERROR] WsSendMessageServiceTest.broadcastsToOtherConnections       » MemberNotFound
[ERROR] WsSendMessageServiceTest.excludesSendersOwnConnection       » MemberNotFound
[ERROR] WsSendMessageServiceTest.persistsBeforeBroadcast            » MemberNotFound
Tests run: 40, Failures: 7
```
(Both reports are illustrative.)

Seven failures across three test classes, for one broken line. `WsSendMessageServiceTest` is red even though `WsSendMessageService` is fine. You have to trace `WsSendMessageService → MessageService → strategy → abstract class` to find the cause.

**How classicists live with this:** look at the **lowest-level** failing test first (here, the strategy's). The cause is almost always there, and everything above it is fallout. It's also why classic suites still keep focused tests per class. "Classic" means *don't mock your own classes*. It doesn't mean *only test from the top*.

---

## D. Design pressure: mockist makes you decide the contract

Imagine writing `WsSendMessageService` **test-first, mockist style**, before `ConnectionRegistryPort` exists. Your first test needs this line:

```java
when(registry.getConnectionsByChannel(5)).thenReturn( ??? );
```

To write `???` for the "nobody subscribed" case, you have to decide right now: **`null` or `Set.of()`?** The mock forces you to design the port from the *caller's* point of view, before any implementation exists to nudge you. You'd write `Set.of()` (who wants a null check in every caller?) and document it on the interface. Then the adapter would be written to meet that contract. The bug in B would never have been written.

This repo went the other way: the adapter was written first, and `null` fell out of `Map.get`. Nobody ever asked the question, because no test stood where the caller stands.

Mockist design pressure also works on dependencies. Try a mockist test for `AuthenticateUserService.login`, and you find you can't mock `JwtUtils.buildRefreshToken` (`AuthenticateUserService.java:46`) without `mockStatic`. The test is telling you that the service depends on something that isn't a port (07 §11).

**The catch, which is B again:** the mock only records the contract you *decided*. Nothing checks that the real adapter keeps it. Mockists close that gap with **contract tests**: the same test suite run against the mock's assumptions *and* the real adapter ([exercise 3](08-unit-testing-exercises.md#exercise-3-in-memory-connection-registry-) is one).

---

## What to take from this

| | Classic | Mockist |
|---|---|---|
| **A.** Refactor without behaviour change | Green ✅ | Red ❌ (false alarm) |
| **B.** Bug between two of your classes | Red ✅ | Green ❌ (missed) |
| **C.** One broken line | Many reds, you trace ⚠️ | Few reds, pinpointed ✅ |
| **D.** Designing a new interface | Little pressure ⚠️ | Forces the contract question ✅ |

The approach chapter 07 recommends for this repo takes the best cell from each row:
- **Use your own classes for real** (strategies inside services, domain models, value objects) → you get A and B.
- **Replace only the ports.** Use fakes where state matters (`InMemoryConnectionRegistryAdapter`), and Mockito stubs where a fake would be heavy. Verify **commands**, not queries → A mostly holds.
- **Keep a focused test per class**, and read the lowest-level failure first → C is manageable.
- **When designing a new port, write the caller's test first** and settle its contract (null vs empty, which exception) in the test → you get D.
