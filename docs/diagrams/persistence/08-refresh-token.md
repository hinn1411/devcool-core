# 08 — Refresh-token rotation: single use by conditional UPDATE

Each refresh token works **exactly once**. Consuming it is one UPDATE that matches only an unconsumed, unexpired row. Two concurrent refreshes with the same cookie get one winner, and the other gets 401.

## Token lifecycle

```mermaid
stateDiagram-v2
    direction LR
    [*] --> Issued: login or rotation (stores SHA-256 of the jti)
    Issued --> Consumed: refresh (consumeIfValid = 1 row), new token issued
    Issued --> Revoked: logout (revoke = 1 row)
    Issued --> Expired: expired_time passes
    Consumed --> [*]: reuse = 0 rows, 401
    Revoked --> [*]: reuse = 0 rows, 401
    Expired --> [*]: 0 rows, 401
```

## Two tabs refresh with the same cookie

```mermaid
sequenceDiagram
    autonumber
    participant T1 as T1 (tab A)
    participant PG as Postgres
    participant T2 as T2 (tab B)

    T1->>PG: UPDATE refresh_token SET consumed_time = now() WHERE jti = h AND consumed_time IS NULL AND expired_time > now()
    PG-->>T1: 1 row (row locked)
    T2->>PG: same UPDATE
    Note over T2,PG: waits for T1's row lock
    T1->>PG: INSERT new refresh_token (hash of new jti)
    T1->>PG: COMMIT, 200 + rotated cookie
    Note over PG: T2 re-checks WHERE: consumed_time is set
    PG-->>T2: 0 rows
    T2-->>T2: 401 RefreshTokenInvalid
```

**Read:** [notes/08-refresh-token.md](notes/08-refresh-token.md)
