# 01 — Per-channel message sequence

Two members post in the same channel at the same moment. The `UPDATE` on the channel row takes a row lock, so the second sender waits and gets the next number. Seqs are gapless and follow commit order. Senders in *other* channels never wait.

```mermaid
sequenceDiagram
    autonumber
    participant T1 as T1 (Alice sends)
    participant PG as Postgres
    participant T2 as T2 (Bob sends)

    Note over PG: channel 7, last_seq = 41
    T1->>PG: BEGIN
    T2->>PG: BEGIN
    T1->>PG: UPDATE channel SET last_seq = last_seq + 1 WHERE id = 7 RETURNING last_seq
    PG-->>T1: 42 (T1 holds the row lock on channel 7)
    T2->>PG: UPDATE channel SET last_seq = last_seq + 1 WHERE id = 7 RETURNING last_seq
    Note over T2,PG: T2 blocks on the row lock
    T1->>PG: INSERT message (channel 7, seq 42)
    T1->>PG: UPDATE member SET last_read_seq ... (sender is caught up)
    T1->>PG: COMMIT (lock released)
    Note over PG: READ COMMITTED re-reads the newest row version: 42
    PG-->>T2: 43
    T2->>PG: INSERT message (channel 7, seq 43)
    T2->>PG: COMMIT
    Note over PG: UNIQUE(channel_id, seq) is the backstop if the code ever skips the lock
```

**The naive version** loses an update: both transactions read the same `MAX(seq)` and write the same number.

```mermaid
sequenceDiagram
    participant T1
    participant PG as Postgres
    participant T2
    T1->>PG: SELECT MAX(seq) FROM message WHERE channel_id = 7
    PG-->>T1: 41
    T2->>PG: SELECT MAX(seq) FROM message WHERE channel_id = 7
    PG-->>T2: 41 (plain SELECT takes no lock)
    T1->>PG: INSERT seq 42
    T2->>PG: INSERT seq 42
    PG-->>T2: duplicate key on uk_message_channel_seq (or a silent duplicate without the key)
```

**Read:** [notes/01-per-channel-seq.md](notes/01-per-channel-seq.md)
