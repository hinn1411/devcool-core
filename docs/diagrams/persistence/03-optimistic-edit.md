# 03 — Edit with optimistic locking

Two tabs edit the same message, both based on `version = 3`. The UPDATE only matches if the version is still 3, so exactly one edit wins and the other gets **409 Conflict**. Nothing is locked while the user types.

```mermaid
sequenceDiagram
    autonumber
    participant T1 as T1 (tab A, v3)
    participant PG as Postgres
    participant T2 as T2 (tab B, v3)

    T1->>PG: SELECT editable row (id 100)
    PG-->>T1: version 3, sender alice, created 2 min ago
    T2->>PG: SELECT editable row (id 100)
    PG-->>T2: version 3
    Note over T1,T2: Both pass the checks: sender, 24 h window, TEXT/MARKDOWN, version matches
    T1->>PG: UPDATE message SET content, version = version + 1 WHERE id = 100 AND version = 3 AND deleted_time IS NULL
    PG-->>T1: 1 row (now v4, row locked until commit)
    T2->>PG: same UPDATE ... AND version = 3
    Note over T2,PG: waits for T1's row lock
    T1->>PG: COMMIT
    Note over PG: T2 re-checks WHERE on the new row: version is 4
    PG-->>T2: 0 rows
    T2-->>T2: MessageVersionConflictException, 409
```

```mermaid
flowchart LR
    A[PATCH with version v] --> B{Row found and live?}
    B -- no --> N[404]
    B -- yes --> C{Member and sender?}
    C -- not a member --> N
    C -- not the sender --> F[403]
    C -- yes --> T{TEXT or MARKDOWN?}
    T -- no --> E1[400]
    T -- yes --> D{Within 24 h?}
    D -- no --> E2[403 window closed]
    D -- yes --> V{v == stored version?}
    V -- no --> K[409 early]
    V -- yes --> S{Same content?}
    S -- yes --> OK0[200, no write]
    S -- no --> U["UPDATE ... WHERE version = v"]
    U -- 1 row --> OK[200, version v+1]
    U -- 0 rows --> K2[409 lost the race]
    classDef ok fill:#d3f9d8,stroke:#2b8a3e,color:#111
    classDef bad fill:#ffe3e3,stroke:#c92a2a,color:#111
    class OK0,OK ok
    class N,F,E1,E2,K,K2 bad
```

**Read:** [notes/03-optimistic-edit.md](notes/03-optimistic-edit.md)
