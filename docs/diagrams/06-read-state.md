# 06 — Read state and O(1) unread counts

Each member row stores `last_read_seq`. The marker only moves forward and is clamped to the channel's `last_seq`. Unread is a subtraction, not a `COUNT(*)` over messages.

## Out-of-order read markers

```mermaid
sequenceDiagram
    autonumber
    participant P as Phone
    participant L as Laptop
    participant PG as Postgres

    Note over PG: channel.last_seq = 60, member.last_read_seq = 30
    L->>PG: PUT /read seq 50
    PG-->>L: last_read_seq = GREATEST(30, LEAST(50, 60)) = 50
    P->>PG: PUT /read seq 40 (stale, arrives late)
    PG-->>P: GREATEST(50, LEAST(40, 60)) = 50, unchanged
    P->>PG: PUT /read seq 999 (buggy client)
    PG-->>P: GREATEST(50, LEAST(999, 60)) = 60, clamped
    Note over PG: unread = last_seq - last_read_seq = 0
```

## Where the marker moves

```mermaid
flowchart LR
    A[Member added] -->|"catchUpMembers: last_read_seq = last_seq"| M[(member.last_read_seq)]
    S[Member sends] -->|"same tx as the insert: advance to own seq"| M
    R[PUT /channels/id/read] -->|"GREATEST(..., LEAST(seq, last_seq))"| M
    C[(channel.last_seq)] --> U["unreadCount = last_seq - last_read_seq"]
    M --> U
    U --> L["Channel list: 1 SQL per page (unread + lastMessage via seq = last_seq)"]
```

**Read:** [notes/06-read-state.md](notes/06-read-state.md)
