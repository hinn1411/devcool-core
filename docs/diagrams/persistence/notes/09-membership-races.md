# 09 — Membership races (notes)

Diagram: [../09-membership-races.md](../09-membership-races.md)

## Problem
A creator and a leader add Bob to a forum at the same moment. Bob must end up a member once, and `total_of_members` must stay equal to the real member count.

## Without it
- **No unique key:** two member rows for Bob, so double notifications and a wrong count, forever.
- **Counter as read-modify-write in Java** (`channel.setTotal(channel.getTotal() + 1); save()`): both read 9, both write 10, and one increment is lost (**lost update**).

## Mechanism
`ChannelService.addMember` in one transaction: role check → dedupe ids → users exist → **pre-check** existing members (precise 409 listing who) → lounge cap → **atomic increment** → insert members → catch up their read markers (06).

```sql
-- ChannelRepository.increaseTotalMembers
UPDATE ChannelEntity SET totalOfMembers = totalOfMembers + :delta WHERE id = :channelId
```
If the insert hits `uk_member_channel_user`, `ApiExceptionHandler` matches **that constraint name only** and returns 409. Any other integrity violation stays a 500, so real bugs aren't masked as conflicts.

## DB concepts
- **Check-then-act (TOCTOU).** A read followed by a write based on it is a race unless something serializes them. Here the unique index does, for duplicates.
- **Atomic increment.** `x = x + :delta` is evaluated on the locked, latest row, so there's no lost update. The UPDATE also serializes concurrent adds to one channel on the channel row lock.
- **Atomicity saves the counter.** The loser's increment rolls back with its failed insert, so the count stays exact.
- **Error translation by constraint name.** That's why V1 gave constraints readable names.

## Trade-offs and the gap
- **The lounge cap (11 people) can be exceeded** (second diagram): `totalOfMembers` is read by the authorization lookup *before* the increment locks the row. It's write skew on a single row's stale read. Fix it with a conditional increment:
  ```sql
  UPDATE channel SET total_of_members = total_of_members + :n
  WHERE id = :id AND (channel_type <> 'LOUNGE' OR total_of_members + :n <= 11)
  -- 0 rows → 400 "lounge full"
  ```
  The other options are `SELECT … FOR UPDATE` on the channel before the check, or a CHECK constraint, which can't see the channel type of another row unless it's denormalized. This gap was found while writing these notes. It's a follow-up, not yet fixed.
- The duplicate race isn't IT-tested end to end. `ApiExceptionHandlerTest` tests the mapping.

## Interview Q&A
- **Why keep the Java pre-check if the constraint exists?** For UX: it lists *which* users are already members. The constraint is for correctness.
- **How do you avoid a lost update on a counter?** Increment in SQL (`x = x + 1`), not in Java. Or use optimistic locking (03).
- **Where else could this code race?** The lounge cap. Show the fix.

## Blog angle
**"Check-then-act: two bugs in one endpoint, and the one-line SQL fixes."**

## Code
- [`ChannelService.addMember`](../../../../src/main/java/com/devcool/application/service/channel/ChannelService.java#L102)
- [`ChannelRepository.increaseTotalMembers`](../../../../src/main/java/com/devcool/adapters/out/persistence/channel/repository/ChannelRepository.java#L25)
- [`ApiExceptionHandler.isMemberUniqueViolation`](../../../../src/main/java/com/devcool/adapters/in/web/handler/ApiExceptionHandler.java#L141)
- Tests: [`ApiExceptionHandlerTest`](../../../../src/test/java/com/devcool/adapters/in/web/handler/ApiExceptionHandlerTest.java), [`MemberAdapterIT.addMembers_startCaughtUpAtLastSeq`](../../../../src/test/java/com/devcool/adapters/out/persistence/member/MemberAdapterIT.java#L70)
