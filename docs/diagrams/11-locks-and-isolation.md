# 11 — Locks, deadlocks and isolation

Everything runs at Postgres's default **READ COMMITTED**. Correctness comes from three tools: **atomic conditional statements**, **one row lock as a serialization point**, and **unique constraints**. Every write path takes its locks in the same order, so the application paths can't deadlock each other.

## Lock order per write path

```mermaid
flowchart TB
    subgraph SEND [Send message]
        direction LR
        S1["1. channel row: NO KEY UPDATE (bump)"] --> S2["2. INSERT message (FK: KEY SHARE on channel, user, parent)"] --> S3["3. sender's member row: NO KEY UPDATE"]
    end
    subgraph ADD [Add members]
        direction LR
        A1["1. channel row: NO KEY UPDATE (+delta)"] --> A2["2. INSERT new member rows"] --> A3["3. UPDATE those new rows (catch up)"]
    end
    subgraph READ [Mark read]
        direction LR
        R1["caller's member row: NO KEY UPDATE (channel only read, no lock)"]
    end
    subgraph MSG [Edit / delete / react]
        direction LR
        M1["one message row (edit, delete)"] ~~~ M2["INSERT reaction (KEY SHARE on message)"]
    end
    subgraph TOK [Refresh / logout]
        direction LR
        T1["1. refresh_token row"] --> T2["2. INSERT new token or bump app_user.token_version"]
    end
    SEND ~~~ ADD ~~~ READ ~~~ MSG ~~~ TOK
```

Rule: **channel → message → member**. No path holds a member row and then asks for a channel row. That reverse order is what a deadlock needs.

## Row-level lock compatibility (why FK checks don't block edits)

| Held \ Requested | FOR KEY SHARE (FK check) | FOR SHARE | FOR NO KEY UPDATE (UPDATE of non-key cols) | FOR UPDATE (DELETE, key change) |
|---|---|---|---|---|
| **FOR KEY SHARE** | ✓ | ✓ | ✓ | ✗ |
| **FOR SHARE** | ✓ | ✓ | ✗ | ✗ |
| **FOR NO KEY UPDATE** | ✓ | ✗ | ✗ | ✗ |
| **FOR UPDATE** | ✗ | ✗ | ✗ | ✗ |

A send's `UPDATE channel` (NO KEY UPDATE) and another send's FK check on that channel (KEY SHARE) don't conflict, and neither do a soft delete of a message and a reaction or reply that points at it.

## A deadlock that *could* happen: V5 contract vs a live send

```mermaid
sequenceDiagram
    autonumber
    participant M as V5 migration tx
    participant PG as Postgres
    participant S as Send tx (channel 7)

    S->>PG: SELECT message by (user, clientMsgId) (replay check)
    Note over S,PG: S holds ACCESS SHARE on table message until commit
    M->>PG: UPDATE channel 7 SET last_seq = last_seq + n
    Note over M,PG: M holds the row lock on channel 7
    S->>PG: UPDATE channel 7 SET last_seq + 1
    Note over S,PG: S waits for M (row lock)
    M->>PG: ALTER TABLE message ALTER seq SET NOT NULL
    Note over M,PG: needs ACCESS EXCLUSIVE, so M waits for S's ACCESS SHARE
    Note over M,S: cycle: M waits for S, S waits for M
    PG-->>S: after deadlock_timeout (1 s) one tx is aborted with 40P01
    Note over M,S: If S is aborted, the client retries with the same clientMsgId (idempotent, 02). If M is aborted, the migrate task fails and the deploy stops (10)
```

**Read:** [notes/11-locks-and-isolation.md](notes/11-locks-and-isolation.md)
