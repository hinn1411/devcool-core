# 00 — Schema and constraints (notes)

Diagram: [../00-schema.md](../00-schema.md)

## Problem
Application checks race. Two requests can both pass "is this user already a member?" before either inserts. Only the database sees every transaction, so it has to hold the invariants.

## Without it
- Duplicate members, two messages with seq 42, double reactions. Each is a silent data bug, found months later.
- An enum renamed in Java but not in old rows: Hibernate fails on read, far from the cause.

## Mechanism
- **Flyway owns the schema** (`V1__baseline.sql` onward). Hibernate only validates (`spring.jpa.hibernate.ddl-auto=validate` in every profile), so an entity that drifts from the DDL fails at startup, not in production traffic.
- **Readable constraint names** (`uk_message_user_client_msg`, `uk_member_channel_user`). The code matches on them to turn one specific violation into a domain answer (a replay in 02, a 409 in 09) and rethrows the rest.
- **Composite keys carry meaning.** `(channel_id, seq)` is the message order, `(user_id, client_msg_id)` is a send attempt, `(message_id, user_id, emoji)` is a reaction.
- **CHECK constraints** on enum columns (`ck_member_role`, `ck_message_content_type`).
- **BIGINT ids** (`V3`) so nothing overflows at 2³¹. Ids come from pooled sequences (`INCREMENT BY 50`, Hibernate allocates 50 ids per `nextval`).

## DB concepts
- **Integrity constraints as concurrency control.** A unique index check sees *committed and in-flight* rows, not just your snapshot. A concurrent duplicate insert waits for the other transaction, then fails or succeeds.
- **Index side effect.** Every UNIQUE constraint creates a B-tree, and `uk_message_channel_seq` doubles as the history index (07).
- **Natural vs surrogate keys.** `message_reaction` uses a natural composite PK (no id column needed). `message` keeps a surrogate `id` *and* a natural key `(channel_id, seq)`.

## Trade-offs
- `refresh_token.user_id` is still `VARCHAR` without an FK (P3-T01 debt). It can't overflow, but the DB can't stop an orphan token.
- `reply_to_id`'s "same channel" rule lives in the service: a CHECK can't look at another row. A composite FK `(reply_to_id, channel_id) → message(id, channel_id)` would enforce it, at the cost of an extra unique index.

## Interview Q&A
- **Why validate instead of `ddl-auto=update`?** `update` never drops or renames, can't backfill, and runs a different DDL on each environment. Migrations are reviewed, versioned and identical everywhere.
- **Constraint or application check?** Both: the app check gives a good error message, and the constraint gives the guarantee.

## Blog angle
**"Your database is the only thing that sees every request."** Hook: the four constraints that stop four real race conditions in a chat app.

## Code
- [`V1__baseline.sql`](../../../../src/main/resources/db/migration/V1__baseline.sql), [`V3__ids_to_bigint.sql`](../../../../src/main/resources/db/migration/V3__ids_to_bigint.sql), [`V7__message_reaction.sql`](../../../../src/main/resources/db/migration/V7__message_reaction.sql)
- Test: [`DevCoolApplicationIT`](../../../../src/test/java/com/devcool/DevCoolApplicationIT.java) (Flyway applies on an empty DB, then `validate` passes)
