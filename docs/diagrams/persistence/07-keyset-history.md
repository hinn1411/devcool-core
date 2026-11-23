# 07 — History: keyset pagination on `(channel_id, seq)`

Scrolling up asks for "20 messages before seq N" (limit 1–100). Reconnecting asks for "everything after seq N". Both are **seeks** into one B-tree, the unique index that already guarantees ordering. No OFFSET, no sort, and no extra index.

## Why OFFSET breaks while a chat is live

```mermaid
sequenceDiagram
    participant C as Client
    participant API
    Note over API: newest first, page size 3. Messages 10, 9, 8 | 7, 6, 5
    C->>API: OFFSET 0 LIMIT 3
    API-->>C: 10, 9, 8
    Note over API: message 11 arrives
    C->>API: OFFSET 3 LIMIT 3
    API-->>C: 8, 7, 6 (8 is a duplicate. With deletes you skip instead)
    C->>API: keyset: seq below 8, LIMIT 3
    API-->>C: 7, 6, 5 (the bound doesn't move when rows arrive)
```

## One index, two directions

```mermaid
flowchart TB
    subgraph IDX ["uk_message_channel_seq: B-tree leaf entries, sorted by (channel_id, seq)"]
        a["(7, 1)"] --- b["(7, 2)"] --- c["..."] --- d["(7, 41)"] --- e["(7, 42)"] --- f["(8, 1)"]
    end
    Q1["beforeSeq=42: WHERE channel_id=7 AND seq below 42 ORDER BY seq DESC LIMIT n"] -->|"seek to (7,42), scan backward"| d
    Q2["afterSeq=1: WHERE channel_id=7 AND seq above 1 ORDER BY seq ASC LIMIT n"] -->|"seek to (7,1), scan forward"| b
    Q3["Channel list preview: seq = last_seq"] -->|point lookup| e
```

**Read:** [notes/07-keyset-history.md](notes/07-keyset-history.md)
