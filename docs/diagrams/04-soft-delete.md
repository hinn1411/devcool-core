# 04 — Soft delete, idempotent

A message is never removed from the table. Deleting stamps `deleted_time` once: the first delete wins, and every repeat (a retry, a second admin, a double click) answers 204 without changing anything.

## Message lifecycle

```mermaid
stateDiagram-v2
    direction LR
    [*] --> Live: SEND (seq assigned)
    Live --> Edited: PATCH by sender within 24 h, version + 1
    Edited --> Edited: PATCH again, version + 1
    Live --> Deleted: DELETE by sender or channel admin
    Edited --> Deleted: DELETE
    Deleted --> Deleted: repeat DELETE = 204, first timestamp kept
    note right of Deleted
        Row and seq stay (the gap is visible as a tombstone).
        Content is nulled in every read: history, reply preview, channel list.
        Edit and react answer 404.
    end note
```

## Two admins delete at once

```mermaid
sequenceDiagram
    autonumber
    participant T1 as T1 (sender)
    participant PG as Postgres
    participant T2 as T2 (forum leader)

    T1->>PG: SELECT deletable row (id 100)
    PG-->>T1: deleted_time NULL
    T2->>PG: SELECT deletable row (id 100)
    PG-->>T2: deleted_time NULL
    T1->>PG: UPDATE message SET deleted_time = t1 WHERE id = 100 AND deleted_time IS NULL
    PG-->>T1: 1 row (row locked)
    T2->>PG: UPDATE ... SET deleted_time = t2 WHERE id = 100 AND deleted_time IS NULL
    Note over T2,PG: waits for T1's row lock
    T1->>PG: COMMIT
    Note over PG: T2 re-checks WHERE: deleted_time is no longer NULL
    PG-->>T2: 0 rows
    Note over T1,T2: Both answer 204. deleted_time = t1 forever
```

**Read:** [notes/04-soft-delete.md](notes/04-soft-delete.md)
