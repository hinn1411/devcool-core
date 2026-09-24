# Improvements — In-Memory Connection Registry

**Date:** 2026-09-23
**Updated:** 2026-09-24
**Target:** `InMemoryConnectionRegistryAdapter` (`src/main/java/com/devcool/adapters/out/realtime/immemory/`)
**Found by:** Exercise 3 in `docs/learning/08-unit-testing-exercises.md` (tests in `InMemoryConnectionRegistryAdapterTest`)

The registry stores `Map<channelId, Set<connectionId>>` and `Map<connectionId, userId>`. Subscriptions belong to a **connection**, not a user: one user can hold several connections (tabs, devices), each with its own subscriptions.

Numbering follows the original list. Fixed items were removed, so #4 is the only one left.

---

## Low

### 4. `getConnectionsByChannel` has an inconsistent return contract
**File:** `InMemoryConnectionRegistryAdapter.java`, method `getConnectionsByChannel`

Introduced by the #1 fix. For a known channel it returns the **live, mutable** internal set. For an unknown channel it returns the **immutable** `Set.of()`. A caller that mutated the result would corrupt the registry in one case and get an `UnsupportedOperationException` in the other.

**Impact (latent):** the only caller today, `WsSendMessageService:43`, just iterates it, so nothing is affected. It matters once a second caller appears.

**Fix:** return one kind consistently: a snapshot with `Set.copyOf(set)` (safe to iterate while other threads subscribe), or `Collections.unmodifiableSet(set)` for a live read-only view.
