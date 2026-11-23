# 06 — Read state and unread counts (notes)

Diagram: [../06-read-state.md](../06-read-state.md)

## Problem
The channel list shows "#general (12)" for 30 channels on every app open. Read receipts come from several devices, out of order, and sometimes with nonsense values.

## Without it
- `COUNT(*) FROM message WHERE channel_id = ? AND seq > ?` per channel: 30 range scans per list load, growing with backlog.
- `SET last_read_seq = :seq`: a late request from a phone moves the marker **backwards**, and old messages become unread again.
- No clamp: a client sends 999 and the unread count goes negative.

## Mechanism
```sql
-- MemberRepository.advanceLastReadSeq
UPDATE member
SET last_read_seq = GREATEST(last_read_seq, LEAST(:seq, channel.last_seq))
FROM channel
WHERE member.user_id = :userId and member.channel_id = :channelId and member.channel_id = channel.id
```
- **0 rows → not a member → 404**, so the membership check and the write are one statement. An older seq still matches (1 row), so it's a successful no-op.
- New members start caught up (`catchUpMembers` copies `channel.last_seq`). A sender is advanced to their own seq **inside the send transaction** (01).
- The channel list is one statement per page: `c.lastSeq - m.lastReadSeq` for unread, and `LEFT JOIN message ON msg.seq = c.lastSeq` for the preview. That join is a point lookup on `UNIQUE(channel_id, seq)`.

## DB concepts
- **Monotonic (forward-only) update.** `GREATEST` makes the write commutative: any order of arrivals ends at the max. That's a CRDT-like "max register" in one SQL expression.
- **Concurrency.** Two concurrent PUTs on one member row: the second waits for the row lock, then READ COMMITTED re-applies `GREATEST` to the *committed* value. The result is always the highest seq (tested with 20 threads).
- **Denormalization.** `channel.last_seq` and `member.last_read_seq` are derived data stored for O(1) reads. They stay correct because they're updated in the same transaction as the event (send, join).
- **`UPDATE … FROM` (Postgres join update).** It reads the channel row without locking it, so read receipts never contend with senders on the channel row.

## Trade-offs
- Unread counts *positions*, so deleted messages after the marker still count. That's accepted, since a tombstone is still "something happened".
- No per-message read receipts ("seen by Bob"). That would be a different model (a per-user marker is enough for "seen up to").

## Interview Q&A
- **How do you get unread counts in O(1)?** Two counters and a subtraction. Both are updated transactionally with the events that change them.
- **What if receipts arrive out of order?** `GREATEST` makes them order-independent, and `LEAST(…, last_seq)` rejects the future.
- **Doesn't the sender's marker update add contention to sends?** It's the sender's own member row, already serialized behind the channel lock, so there's no new wait.

## Blog angle
**"Unread counts at O(1): two numbers and a subtraction."** Hook: why `COUNT(*)` doesn't scale and how `GREATEST` handles multi-device chaos.

## Code
- [`MemberRepository.advanceLastReadSeq`](../../../../src/main/java/com/devcool/adapters/out/persistence/member/repository/MemberRepository.java#L42), [`catchUpMembers`](../../../../src/main/java/com/devcool/adapters/out/persistence/member/repository/MemberRepository.java#L58)
- [`ChannelRepository.findChannelPageByMemberId`](../../../../src/main/java/com/devcool/adapters/out/persistence/channel/repository/ChannelRepository.java#L72)
- [`ReadStateService.markRead`](../../../../src/main/java/com/devcool/application/service/channel/ReadStateService.java)
- Tests: [`MemberAdapterIT`](../../../../src/test/java/com/devcool/adapters/out/persistence/member/MemberAdapterIT.java) (`advance_movesForward_neverBack`, `advance_clampsToLastSeq`), [`ChannelReadIT.concurrentReads_endAtTheHighestSeq`](../../../../src/test/java/com/devcool/adapters/in/web/controller/ChannelReadIT.java#L102), [`ChannelAdapterIT`](../../../../src/test/java/com/devcool/adapters/out/persistence/channel/ChannelAdapterIT.java) (`unreadCount_isLastSeqMinusLastReadSeq`, `aPageOfChannels_isOneStatement`)
