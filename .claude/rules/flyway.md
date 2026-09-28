---
paths:
  - "src/main/resources/db/migration/**"
  - "src/main/java/**/entity/**/*.java"
---

# Flyway migration rules

- **Never edit a migration that is already on `origin/master`.** A PreToolUse hook blocks it. Add a new migration instead.
- Naming: `V<n>__<snake_case_description>.sql`, where `<n>` is the next integer. The `/flyway-migration` skill computes it.
- Every entity change needs a matching migration in the same change. `ddl-auto=validate` fails the app at startup otherwise.
- **Expand/contract** for anything a running old version depends on. Rolling ECS deploys run old and new code side by side:
  1. Expand: add the nullable column or new table; backfill; the new code writes both.
  2. Contract, in a later release: add NOT NULL/unique constraints, drop the old column.
- Backfills over large tables go in batches, not one `UPDATE`.
- Create indexes on existing large tables with `CREATE INDEX CONCURRENTLY` in their own migration, with `-- flyway:executeInTransaction=false` at the top.
- Use `BIGINT` for ids, `TIMESTAMPTZ` for times, and `TEXT` or bounded `VARCHAR` for strings.
- `CREATE EXTENSION IF NOT EXISTS vector;` lives in its own migration (P8-T01).
