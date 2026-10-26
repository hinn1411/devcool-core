# 0014 — Partial PATCH: a missing or null field keeps its stored value

- **Status:** Accepted
- **Date:** 2026-10-26
- **Phase:** P3

## Context
`PATCH /api/v1/channels/{id}` used to replace every column the request carried: `UPDATE … SET name = :name, channelType = :channelType, expiredTime = :expiredTime`. A field left out was written as `NULL`, and `boundaryType` was never saved at all. P3-T00 added a rule that a lounge expiry must be more than 7 days ahead (`domain/channel/policy/ChannelUpdatePolicy.java`). Under full replace, a rename has to resend the expiry, so a lounge could not even be renamed during its last week. P3-T06 (`PATCH /messages/{id}`) will be the next PATCH endpoint and needs the same convention.

## Decision drivers
- A client sends only what it changes: a rename must not have to repeat the expiry, type or boundary.
- Validation rules run only on the fields that were sent (`ChannelUpdatePolicy.validateUpdate`).
- One conditional `UPDATE`, with no read-modify-write in the service (`ChannelServiceTest` asserts the aggregate is never loaded).
- Easy to explain in an interview, with no new dependency.

## Options considered
### Option A — Keep full replace (PUT semantics on PATCH)
- Pros: no code change; one obvious meaning for every field.
- Cons: every client must read before it writes, and a stale read overwrites someone else's change (a lost update). It caused the lounge-rename bug above. It's also not what PATCH means (RFC 5789).

### Option B — Missing or null = keep, merged with `COALESCE` in SQL (chosen)
- Pros: one statement, `SET c.x = COALESCE(:x, c.x)` (`ChannelRepository.updateChannelInfo`); no extra read; the DTO stays a plain record; the policy treats `null` as "unchanged".
- Cons: a field can't be cleared through the API (for example removing a lounge expiry), and nothing tells a missing field from an explicit `null`.

### Option C — JSON Merge Patch (RFC 7396)
Absent means keep; explicit `null` means clear. The body is bound as a `JsonNode` (`has(field)` vs `get(field).isNull()`) or through a tri-state wrapper per field.
- Pros: the standard answer, and fields can be cleared.
- Cons: the DTO is no longer a typed record, so Bean Validation and the MapStruct mappers need rework; three states per field go through the controller, mapper, command and policy; the SQL needs a "set to null" branch per column.

### Option D — JSON Patch (RFC 6902)
A list of `op/path/value` operations.
- Pros: the most expressive (test, move, array operations).
- Cons: far too much for flat channel and message records; the API is hard for the frontend to use, and validation runs per operation.

| Criterion | A full replace | B COALESCE | C Merge Patch | D JSON Patch |
|---|---|---|---|---|
| Rename without resending other fields | ✗ | ✓ | ✓ | ✓ |
| Can clear a field | ✓ (by omission, accidental) | ✗ | ✓ | ✓ |
| Lost-update risk from stale reads | high | low | low | low |
| Changes to DTO, mapper and policy | none | small | large | large |
| New dependency | no | no | maybe | maybe |

## Decision
Every PATCH endpoint treats a missing or `null` field as "keep the stored value". Merging happens in one SQL `UPDATE` with `COALESCE`. Domain rules validate only the fields that were sent.

## Consequences
- Easier: partial updates from the SPA; validation rules don't fire on fields the client left out; no read-modify-write race in the service.
- Harder: clearing a field needs its own explicit operation. Removing a lounge expiry is out of scope today.
- Every new PATCH endpoint (P3-T06 edit message) must document "missing = keep" in its `@Schema`, and needs an IT showing that a field left out is kept.
- `COALESCE` on a converted or enum column is checked against real Postgres (`ChannelControllerAuthzIT.renameOnly_keepsTypeAndExpiry_ofALoungeExpiringSoon`).

## Revisit when
A product requirement needs a field to be cleared through PATCH (for example "remove the lounge expiry"), or a PATCH resource has more than ~5 optional fields with clear semantics. Then move to Option C for that resource.
