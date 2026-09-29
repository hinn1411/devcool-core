---
name: flyway-migration
description: Create a new Flyway SQL migration with the correct next version number and DevCool's expand/contract rules. Use whenever a schema change, index, extension or data backfill is needed, or an entity's columns change.
argument-hint: <short description, e.g. add message seq>
paths: "src/main/resources/db/migration/**,src/main/java/**/entity/**"
---

# New Flyway migration: $ARGUMENTS

Existing migrations (the highest version wins):
!`find src/main/resources -path '*db/migration*' -name 'V*.sql' | sort -V`

## Steps
1. Next version = highest `V<n>` above + 1. If there are none, this is `V1__baseline.sql` (task P1-T01).
2. File name: `src/main/resources/db/migration/V<n>__<snake_case_description>.sql`.
3. Write the SQL following `.claude/rules/flyway.md`:
   - BIGINT ids, TIMESTAMPTZ times.
   - **Expand/contract.** In this migration, add nullable columns/new tables and backfill. Add NOT NULL/unique constraints and drops in a *later* migration, once code writing the new shape is deployed.
   - `CREATE INDEX CONCURRENTLY` on large existing tables, in its own file, starting with the line `-- flyway:executeInTransaction=false`.
4. Update the matching JPA entity (`adapters/out/persistence/**/entity`) in the same change, so `ddl-auto=validate` passes.
5. Verify against a real Postgres: run the Testcontainers ITs (`./mvnw -Dit verify`), or start the app with the `local` profile and check that the Flyway log applies the new version.
6. Never modify a migration that exists on `origin/master`. The PreToolUse hook blocks it; don't work around it.
