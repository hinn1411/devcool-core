# 02 — Idempotent send (`clientMsgId`)

The client generates a UUID per message and resends it on retry. The unique key `(user_id, client_msg_id)` makes a retry return the original message instead of a duplicate, and a retry never moves `last_seq`.

## The send path

```mermaid
flowchart TD
    A[SEND with clientMsgId] --> B[Validate content and check membership]
    B --> C
    subgraph TX1 [tx 1: saveOnce]
        C{"Replay? SELECT by (user_id, client_msg_id)"}
        C -- not found --> D[Check the reply target]
        D --> E["Bump channel.last_seq (row lock)"]
        E --> F[INSERT message + saveAndFlush]
        F -- ok --> G[Advance the sender's read marker]
        F -- "unique violation on uk_message_user_client_msg" --> I["setRollbackOnly: undoes the bump"]
    end
    subgraph TX2 [tx 2: rereadWinner]
        J[SELECT the winner by key]
    end
    C -- found in another channel --> X[409 ClientMsgIdReused]
    C -- found --> R["Return the original id and seq (duplicate=true)"]
    G --> H[COMMIT and return the new seq]
    I --> J
    J --> R
    classDef ok fill:#d3f9d8,stroke:#2b8a3e,color:#111
    classDef bad fill:#ffe3e3,stroke:#c92a2a,color:#111
    class H,R ok
    class X,I bad
```

## The retry race (two retries of one key)

```mermaid
sequenceDiagram
    autonumber
    participant T1 as T1 (send, key K)
    participant PG as Postgres
    participant T2 as T2 (retry, key K)

    T1->>PG: SELECT by (alice, K)
    PG-->>T1: none
    T2->>PG: SELECT by (alice, K)
    PG-->>T2: none (both miss the replay check)
    T1->>PG: bump last_seq, gets 1 (row lock)
    T2->>PG: bump last_seq
    Note over T2,PG: waits on the channel row lock
    T1->>PG: INSERT (alice, K, seq 1)
    T1->>PG: COMMIT
    PG-->>T2: 2
    T2->>PG: INSERT (alice, K, seq 2)
    PG-->>T2: ERROR unique violation (sees T1's committed row)
    Note over T2,PG: The transaction is now aborted (25P02): any further statement fails
    T2->>PG: ROLLBACK (last_seq goes back to 1)
    T2->>PG: new transaction: SELECT by (alice, K)
    PG-->>T2: id of T1's row, seq 1 (returned as duplicate)
```

**Read:** [notes/02-idempotent-send.md](notes/02-idempotent-send.md)
