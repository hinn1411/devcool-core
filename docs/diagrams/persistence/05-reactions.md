# 05 — Reactions: upsert toggle and one-query counts

A reaction is a row keyed by `(message_id, user_id, emoji)`. Adding the same reaction twice is a no-op thanks to `ON CONFLICT DO NOTHING`. The history page loads every message's counts in **one** grouped query.

## Concurrent double-tap

```mermaid
sequenceDiagram
    autonumber
    participant T1 as T1 (tap 1)
    participant PG as Postgres
    participant T2 as T2 (tap 2)

    T1->>PG: INSERT (100, alice, 👍) ON CONFLICT (message_id, user_id, emoji) DO NOTHING
    PG-->>T1: 1 row (index entry in flight)
    T2->>PG: same INSERT
    Note over T2,PG: same key in flight: waits for T1's outcome
    T1->>PG: COMMIT
    PG-->>T2: 0 rows (conflict, nothing to do)
    T2->>PG: COMMIT
    Note over PG: exactly one row. Both callers answer 204
```

## Counts for a history page (no N+1)

```mermaid
flowchart LR
    H["History page: 50 messages (1 query, JOIN FETCH)"] --> IDS[collect message ids]
    IDS --> Q["1 query: GROUP BY message_id, emoji with count(*) and bool_or(user_id = viewer)"]
    Q --> M[merge into each MessageItem]
    IDS -. "naive: 50 queries" .-> X[N+1]
    classDef bad fill:#ffe3e3,stroke:#c92a2a,color:#111
    class X bad
```

## Known gap: the 20-emoji cap is check-then-act

```mermaid
sequenceDiagram
    participant T1 as T1 (adds 🔥)
    participant PG as Postgres
    participant T2 as T2 (adds 🎉)
    Note over PG: message has 19 distinct emojis
    T1->>PG: SELECT DISTINCT emoji
    PG-->>T1: 19, below the cap, OK
    T2->>PG: SELECT DISTINCT emoji
    PG-->>T2: 19, below the cap, OK
    T1->>PG: INSERT 🔥
    T2->>PG: INSERT 🎉 (different key, no conflict)
    Note over PG: 21 distinct emojis: write skew. Accepted (cosmetic limit), see notes
```

**Read:** [notes/05-reactions.md](notes/05-reactions.md)
