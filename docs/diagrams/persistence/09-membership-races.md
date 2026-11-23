# 09 — Membership races: pre-check for the message, constraint for the truth

Adding members checks "already a member?" in Java for a precise error, but two concurrent adds can both pass that check. `UNIQUE(user_id, channel_id)` stops the duplicate and the handler turns it into 409. The member counter uses an atomic `+ delta` and rolls back with the failed insert.

## Duplicate add race

```mermaid
sequenceDiagram
    autonumber
    participant T1 as T1 (creator adds Bob)
    participant PG as Postgres
    participant T2 as T2 (leader adds Bob)

    T1->>PG: SELECT members of channel 7 in (bob)
    PG-->>T1: none
    T2->>PG: SELECT members of channel 7 in (bob)
    PG-->>T2: none (both pass the pre-check)
    T1->>PG: UPDATE channel SET total_of_members = total_of_members + 1 WHERE id = 7
    PG-->>T1: 1 row (channel row locked)
    T2->>PG: same UPDATE
    Note over T2,PG: waits for the channel row lock
    T1->>PG: INSERT member (bob, 7) + catch up last_read_seq
    T1->>PG: COMMIT
    PG-->>T2: 1 row (re-applied to the committed count)
    T2->>PG: INSERT member (bob, 7)
    PG-->>T2: ERROR duplicate key uk_member_channel_user
    T2->>PG: ROLLBACK (its + 1 is undone)
    T2-->>T2: 409 Conflict
```

## Known gap: the lounge cap reads the count before the lock

```mermaid
sequenceDiagram
    participant T1 as T1 (adds 2)
    participant PG as Postgres
    participant T2 as T2 (adds 2 others)
    Note over PG: lounge, total_of_members = 9, cap 11
    T1->>PG: SELECT channel_type, total_of_members (no lock)
    PG-->>T1: 9, and 9 + 2 is within the cap
    T2->>PG: SELECT channel_type, total_of_members (no lock)
    PG-->>T2: 9, and 9 + 2 is within the cap
    T1->>PG: total + 2, insert, COMMIT (11)
    T2->>PG: total + 2, insert, COMMIT (13)
    Note over PG: cap exceeded. Fix: put the cap in the UPDATE's WHERE (see notes)
```

**Read:** [notes/09-membership-races.md](notes/09-membership-races.md)
