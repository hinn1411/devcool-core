# 00 — Core schema and constraints

The chat tables after `V7`. Every concurrency technique in this folder relies on one of these keys. The service layer checks first so it can give a friendly error, and the constraint guarantees the rule.

```mermaid
erDiagram
    app_user ||--o{ member : "joins"
    channel  ||--o{ member : "has"
    channel  ||--o{ message : "contains"
    app_user ||--o{ message : "sends"
    message  |o--o{ message : "reply_to"
    message  ||--o{ message_reaction : "has"
    app_user ||--o{ message_reaction : "reacts"

    app_user {
        bigint id PK "pooled sequence, +50"
        varchar user_name UK
        varchar email UK
        int token_version "bumped to revoke access tokens"
        varchar role "CHECK in (USER, ADMIN)"
    }
    channel {
        bigint id PK
        varchar channel_type "CHECK in (LOUNGE, FORUM, PRIVATE_CHAT)"
        int total_of_members "atomic += delta"
        bigint last_seq "row-locked counter (01)"
        timestamptz last_message_at
    }
    member {
        bigint id PK
        bigint user_id FK "UK(user_id, channel_id)"
        bigint channel_id FK
        varchar role "CHECK in (MEMBER, CREATOR, LEADER)"
        bigint last_read_seq "forward-only (06)"
    }
    message {
        bigint id PK "global reference"
        bigint channel_id FK "UK(channel_id, seq) = order + history index"
        bigint seq "NOT NULL since V5"
        bigint user_id FK "UK(user_id, client_msg_id) = idempotency"
        uuid client_msg_id
        int version "optimistic lock (03)"
        bigint reply_to_id FK "same channel, checked in service"
        timestamptz deleted_time "soft delete (04)"
    }
    message_reaction {
        bigint message_id PK, FK "PK(message_id, user_id, emoji)"
        bigint user_id PK, FK
        varchar emoji PK "varchar(16) = code points"
        timestamptz created_time
    }
    refresh_token {
        varchar jti PK "SHA-256 of the jti, never the raw value"
        varchar user_id
        timestamptz expired_time
        timestamptz consumed_time "single use (08)"
    }
```

| Constraint | What it guarantees | Used by |
|---|---|---|
| `uk_message_channel_seq (channel_id, seq)` | no two messages share a position; also the history index | [01](01-per-channel-seq.md), [07](07-keyset-history.md) |
| `uk_message_user_client_msg (user_id, client_msg_id)` | one row per send attempt | [02](02-idempotent-send.md) |
| `pk_message_reaction (message_id, user_id, emoji)` | one reaction per user per emoji | [05](05-reactions.md) |
| `uk_member_channel_user (user_id, channel_id)` | a user joins a channel once | [09](09-membership-races.md) |
| `ck_*` CHECKs on enum columns | the DB rejects values the Java enum doesn't know | all |

**Read:** [notes/00-schema.md](notes/00-schema.md)
