# 10 — Zero-downtime migrations: expand → deploy → contract

Adding `message.seq NOT NULL` while old tasks keep inserting without it takes three steps: **expand** (nullable column + backfill), **deploy** the code that writes it, then **contract** (fill the stragglers and set NOT NULL). Every migration caps how long it may wait for a lock.

## The seq rollout (V4 → P3-T03 → V5)

```mermaid
flowchart LR
    subgraph E ["V4 expand (P3-T02)"]
        E1["ADD COLUMN seq NULL, last_seq DEFAULT 0 (metadata only)"] --> E2["Backfill: row_number() OVER (PARTITION BY channel_id ORDER BY id)"]
        E2 --> E3["Set channel.last_seq = max(seq)"]
        E3 --> E4["ADD UNIQUE (channel_id, seq), UNIQUE (user_id, client_msg_id)"]
    end
    subgraph D ["Rolling deploy (P3-T03)"]
        D1["Old tasks: INSERT without seq (NULL allowed)"]
        D2["New tasks: bump last_seq, INSERT with seq"]
    end
    subgraph C ["V5 contract (P3-T03a)"]
        C1["Bump channel.last_seq by the NULL count FIRST (send lock order)"] --> C2["Number NULL rows after the old last_seq"]
        C2 --> C3["ALTER seq SET NOT NULL"]
    end
    E --> D --> C
```

## How a migration reaches production

```mermaid
sequenceDiagram
    autonumber
    participant GHA as deploy.yml
    participant MIG as ECS one-off task (ecs,migrate)
    participant PG as Aurora Postgres
    participant SVC as api service (N tasks)

    GHA->>MIG: run-task, same image as the new release
    MIG->>PG: pg_advisory_lock (Flyway: only one migrator)
    MIG->>PG: BEGIN, SET LOCAL lock_timeout = 5s, DDL + backfill, COMMIT
    alt lock not acquired within 5 s
        PG-->>MIG: ERROR lock timeout, rolled back
        MIG-->>GHA: exit 1, deploy stops before any task changes
    else migrated
        MIG->>PG: ddl-auto=validate against the entities
        MIG-->>GHA: exit 0
        GHA->>SVC: rolling update (old and new code both run on the expanded schema)
    end
```

**Read:** [notes/10-migrations.md](notes/10-migrations.md)
