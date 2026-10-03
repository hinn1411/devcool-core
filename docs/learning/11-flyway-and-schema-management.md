# 11 — Flyway & Schema Management

The theme: **who owns the schema?** Today the answer is "Hibernate, guessing from annotations" locally, and "nobody" in `ecs`. P1-T01 makes the answer "versioned SQL files in git". This chapter is the background for that task.

---

## 1. `ddl-auto`: Hibernate as a schema tool

**The concept.** `spring.jpa.hibernate.ddl-auto` maps to Hibernate's `hibernate.hbm2ddl.auto`. At startup, Hibernate compares its entity metamodel to the database and does one of five things:

| Value | At startup | At shutdown | Use it for |
|---|---|---|---|
| `create` | drops every mapped table, then creates them | — | throwaway demos |
| `create-drop` | same as `create` | drops everything | embedded-DB tests (the Spring Boot default for H2 when no Flyway is present) |
| `update` | **adds** missing tables and columns | — | prototyping only |
| `validate` | compares the schema to the entities and **fails startup** on a mismatch, without changing anything | — | every real environment, together with Flyway |
| `none` | nothing | — | when another tool owns the schema and you don't want the check |

The trap is `update`. It sounds like "keep the schema in sync", but it can only **add**. It never:

- drops a column you deleted from the entity, so the column stays, often `NOT NULL`, and inserts start failing;
- renames anything (a rename looks like "drop A, add B", and it does neither drop);
- narrows a type or a length (`length = 50 → 20` is ignored);
- moves data.

**And it writes no record.** Two developers' local databases drift apart, and nobody can say what production looks like, because the schema is a side effect of whichever entity version last booted against it.

**In your code** — `application-local.properties:9`:

```properties
spring.jpa.hibernate.ddl-auto=update
```

And a leftover in `application.properties:5`:

```properties
spring.jpa.generate-ddl=true
```

`generate-ddl` is the vendor-neutral JPA switch (Hibernate reads it as `update`). An explicit `ddl-auto` overrides it, so today it does nothing. It still misleads anyone who reads it: it says "the app creates its own tables" in the file every profile inherits. P1-T01 should delete it.

---

## 2. Flyway: the schema as an append-only log

**The concept.** Flyway treats the schema like an event log. Each change is an immutable SQL file, applied once, in order, and recorded.

```
src/main/resources/db/migration/       ← default location (classpath:db/migration)
  V1__baseline.sql
  V2__message_channel_seq_index.sql
  V3__...
```

The filename format is strict: `V` + version + **two** underscores + description + `.sql`. `V2_index.sql` (one underscore) is silently not recognised as a migration.

On startup Flyway:

1. Creates `flyway_schema_history` if it isn't there.
2. Reads which versions are already applied, each with a **checksum** of the file's contents.
3. **Validates.** If an applied file's checksum changed, it stops with `Migration checksum mismatch`.
4. Applies each pending file in version order. On Postgres, each one runs in its own transaction.
5. Inserts a history row per file (version, description, checksum, who, when, how long, success).

```
 installed_rank | version | description     | checksum    | success
----------------+---------+-----------------+-------------+--------
              1 | 1       | baseline        | -1234567890 | t
              2 | 2       | message channel | 987654321   | t
```

**The rules that follow from this:**

- **Never edit an applied migration.** It has already run on some database, so editing the file changes nothing there. It only breaks the checksum. Fix forward with `V3__fix_....sql`.
- **Postgres DDL is transactional**, so a failed migration rolls back cleanly. (MySQL can't do this, which is a point in Postgres's favour worth knowing.) The exception is `CREATE INDEX CONCURRENTLY`, which can't run inside a transaction. Put it alone in its own file, with `-- flyway:executeInTransaction=false` at the top (the rule in `.claude/rules/flyway.md`; see §7e). That matters for P1-T03 once `message` is large. For now a plain `CREATE INDEX` is fine.
- **Repeatable migrations** (`R__name.sql`) have no version. They re-run whenever their checksum changes, after all `V` files. They suit views and functions, not tables.
- **Defaults that protect you:** `validate-on-migrate=true` (the checksum check), and `clean-disabled=true` since Flyway 10 (`flyway clean` drops everything, and it's off unless you enable it on purpose).

**Why it matters.** The schema now has the same properties as your code: it's reviewed in a PR, versioned, and reproducible from nothing. `docker compose down -v` followed by a boot gives every developer, CI and Aurora the same schema. That's the first line of P1's Definition of Done.

---

## 3. How Spring Boot wires them together

**The concept.** When `flyway-core` is on the classpath and `spring.flyway.enabled=true` (the default), Spring Boot's `FlywayAutoConfiguration` runs `flyway.migrate()` during context startup. It also makes the JPA `EntityManagerFactory` **depend on** the Flyway bean. So the order is always:

```
DataSource ready
   │
   ▼
Flyway migrate            ← brings the schema to the latest version
   │
   ▼
Hibernate starts          ← ddl-auto=validate checks the entities against that schema
   │
   ▼
Web server, controllers…
```

That's why the pair is **Flyway + `validate`**:

- Flyway is the only writer of the schema.
- `validate` is a startup assertion that the entities and the migrations agree. If someone adds a field to `UserEntity` and forgets the migration, the app refuses to start. Without that check it would fail at the first `INSERT` in production.

The properties P1-T01 touches:

| Property | Default | Meaning |
|---|---|---|
| `spring.flyway.enabled` | `true` | Run migrations at startup |
| `spring.flyway.locations` | `classpath:db/migration` | Where the `V*.sql` files live |
| `spring.flyway.baseline-on-migrate` | `false` | On a non-empty schema with no history table, mark it as already at `baseline-version` instead of failing |
| `spring.flyway.baseline-version` | `1` | The version that marking records |
| `spring.jpa.hibernate.ddl-auto` | `none` when Flyway is present | Set it to `validate` explicitly, so the intent is visible |

---

## 4. Where this repo stands today

**In your code** — `application-ecs.properties:7-10`:

```properties
# ECS JPA: don't destroy schema
spring.jpa.hibernate.ddl-auto=validate

# ECS Flyway: usually ON in real deployments (you can keep false for now)
spring.flyway.enabled=false
```

`src/main/resources/db/migration/` doesn't exist. So `ecs` validates against a schema that nothing in the repo can create. Against a fresh Aurora it fails on the first table: `Schema-validation: missing table [app_user]`. That's audit item #8 in the [README](README.md#fix-these-first).

**A dependency gap you'll hit first** — `pom.xml:72-75`:

```xml
<dependency>
    <groupId>org.flywaydb</groupId>
    <artifactId>flyway-core</artifactId>
</dependency>
```

Spring Boot 3.5.6 brings in Flyway **11.7.2**. Since Flyway 10, each database's support is a separate module, and `flyway-core` alone doesn't know Postgres. As soon as you set `enabled=true`, startup fails with `Unsupported Database: PostgreSQL 16.x`. Add:

```xml
<dependency>
    <groupId>org.flywaydb</groupId>
    <artifactId>flyway-database-postgresql</artifactId>
</dependency>
```

(No version: the Spring Boot parent manages it. CLAUDE.md asks you to check dependencies with context7. This one was checked against the Flyway docs: *"PostgreSQL is found within the `flyway-database-postgresql` plugin module."*)

**Two plans that look like they conflict.** The P1 design note says to enable Flyway in **every** profile, including `ecs`. P2's [phase file](../plans/phases/phase-2-infra-cicd-walking-skeleton.md) (P2-T05) turns it **off** in `ecs` again and runs it as a one-off migrate ECS task before the deploy. Both are right, in sequence:

- **P1:** there's no AWS deployment yet, so "Flyway at startup everywhere" is the simplest way to have the schema owned by migrations.
- **P2:** with N tasks running, every task tries to migrate at startup. Flyway's lock keeps that correct, but it isn't operable: startups block, a failed migration crash-loops the rollout, and the app needs DDL rights. A single migrate step before the rollout fails the deploy before any task changes. [§7](#7-migrating-when-many-tasks-start-at-once) covers this in detail.

---

## 5. Traps when generating the baseline from these entities

P1-T01 says to generate DDL from the entities, then **review it by hand**. These are the specific things to review, all taken from this codebase.

### 5a. Sequences: name and increment

Every entity uses `@GeneratedValue(strategy = GenerationType.SEQUENCE)` with no `@SequenceGenerator` (for example `ChannelEntity.java:21`). Hibernate 6's default ("standard") naming strategy then expects **one sequence per table, named `<table>_seq`**, with JPA's default `allocationSize = 50`:

```sql
CREATE SEQUENCE app_user_seq START WITH 1 INCREMENT BY 50;
CREATE SEQUENCE channel_seq  START WITH 1 INCREMENT BY 50;
```

`INCREMENT BY 50` is not a typo. Hibernate takes one sequence value and hands out 50 ids from memory (the pooled optimizer). If V1 says `INCREMENT BY 1`, Hibernate 6 refuses to start because the sequence increment doesn't match the mapping. If you got past that, ids would collide.

(Hibernate 5 used a single shared `hibernate_sequence`. Old blog posts show that, and they're wrong for this project.)

### 5b. Uppercase names vs Postgres case folding

The entities use uppercase names: `@Table(name = "APP_USER")` (`UserEntity.java:11`) and `@Column(name = "USER_NAME")`. Spring Boot's default physical naming strategy lowercases them to `app_user` and `user_name`. Postgres also folds **unquoted** identifiers to lowercase. So:

```sql
CREATE TABLE app_user (...)      -- ✅ matches
CREATE TABLE APP_USER (...)      -- ✅ also fine: unquoted, so folded to app_user
CREATE TABLE "APP_USER" (...)    -- ❌ a different table, and validate fails
```

Write V1 in lowercase and unquoted. `pg_dump` already produces it that way.

### 5c. Enum columns become CHECK constraints

`@Enumerated(EnumType.STRING)` (for example `UserEntity.java:44-49` for `ROLE` and `STATUS`) generates `varchar(255)` **plus** a constraint in Hibernate 6.2+:

```sql
role varchar(255) not null check (role in ('ADMIN','USER'))
```

That's good: the DB rejects garbage. The cost is that **adding an enum value in Java needs a migration** that drops and re-creates the check. Otherwise the first insert of the new value fails, and `validate` won't warn you, because it doesn't compare check constraints. Decide on purpose whether to keep these checks, and write that down in the PR.

### 5d. Generated noise to remove

`ChannelEntity.java:22` has `@Column(name = "ID", nullable = false, unique = true)` on the `@Id`. A primary key is already unique, so the generator adds a redundant `UNIQUE` constraint and index. Generated DDL also has machine names such as `FKabc123xyz` for foreign keys. Rename them to readable ones (`fk_channel_creator`) now; it's free in V1 and needs its own migration later.

### 5e. What V1 must *not* do

- **Don't change id types.** `Integer` → `BIGINT` is P3-T01, as its own migration. V1 records the schema as it is today.
- **Leave out pgvector for now.** ADR-0009 says `CREATE EXTENSION vector` goes in a migration, but nothing uses it until P8. Add it in the migration that first needs it.

### 5f. Your local DB isn't empty

`ddl-auto=update` already built tables in your local volume. With Flyway on, startup fails:

```
Found non-empty schema(s) "public" but no schema history table.
Use baseline() or set baselineOnMigrate to true to initialize the schema history table.
```

There are two ways out:

| Option | When it fits |
|---|---|
| `docker compose down -v`, then boot | **Here.** Local data is disposable, and an empty schema is the only way to prove V1 builds the full schema (the DoD). |
| `baseline-on-migrate=true` | A database whose data matters and whose schema already equals V1. Flyway records V1 as applied **without running it**, so it's only safe if that's actually true. |

`baseline-on-migrate` is the right tool for an existing production database. Here it would hide exactly the bug you're trying to catch.

---

## 6. Two ways to produce V1

| | Hibernate schema-generation script | `pg_dump --schema-only` |
|---|---|---|
| How | Set `spring.jpa.properties.jakarta.persistence.schema-generation.scripts.action=create` and `...scripts.create-target=target/v1.sql`, then boot once | Boot once with `ddl-auto=create` on an empty DB, then run `pg_dump --schema-only --no-owner --no-privileges` |
| Output | What Hibernate **thinks** the schema is | What Postgres **actually** has |
| Noise | Low | High (`SET` statements, `ALTER ... OWNER`, comments) |

Either one is a draft. The review checklist before committing:

- [ ] One `_seq` per table, `INCREMENT BY 50` (§5a)
- [ ] Lowercase, unquoted identifiers (§5b)
- [ ] An enum CHECK decision made and noted (§5c)
- [ ] Readable FK, UK and index names; no redundant unique on PKs (§5d)
- [ ] No id type changes, no extensions (§5e)
- [ ] `docker compose down -v` → boot with `validate` → it starts. Then the IT in P1-T11 makes that check automatic.

---

## 7. Migrating when many tasks start at once

**The setting.** From P2, the `api` service runs as N ECS tasks from one image. A deploy is **rolling**: ECS starts new tasks, waits for them to pass the ALB health check, then stops old ones. For a while, old and new code run side by side against the same database.

If Flyway runs at startup (`spring.flyway.enabled=true` in `ecs`), every new task calls `migrate()`.

### 7a. It's not a classic race, because Flyway takes a lock

A common first guess is that "N tasks migrate at once" applies V5 N times. It doesn't. Before migrating, Flyway takes a **Postgres advisory lock**, so only one process migrates at a time:

```
t0  task A: lock acquired → applying V5 ...
t0  task B: waiting for lock
t0  task C: waiting for lock
t1  task A: V5 done, history row written, lock released → Hibernate validate → serving
t1  task B: lock acquired → history says V5 applied → nothing to do → serving
t1  task C: same as B
```

So the result is **correct**. The problems are about how it behaves during a deploy, not about the data.

### 7b. The real problems

**1. Startup blocks on the migration, and health checks don't wait.**
Task A holds the lock for as long as the migration takes. Tasks B and C can't serve traffic until then. A fast `ALTER TABLE ADD COLUMN` takes milliseconds. A backfill or an index on a large `message` table takes minutes. If that's longer than the health-check grace period, ECS kills task A **mid-migration**:

- A transactional migration rolls back. The next task starts it again, gets killed again, and the deploy loops.
- A non-transactional one (`CREATE INDEX CONCURRENTLY`) is left half done: an `INVALID` index and a `success = false` row in `flyway_schema_history`. Flyway then refuses to migrate until someone cleans up by hand and runs `flyway repair`.

**2. A failed migration crash-loops the rollout.**
If V5 has a bug, every new task fails at startup. ECS keeps starting replacements. The deployment circuit breaker eventually rolls back, if it's enabled. Meanwhile the deploy is half running, alarms fire, and the logs are full of N copies of the same error. The failure happens **during** the rollout, not before it.

**3. The app's database user needs DDL rights.**
To migrate at startup, the user the app connects with must be allowed to `CREATE`, `ALTER` and `DROP`. Any bug that lets an attacker run SQL through the app (an injection, an unsafe native query) can then drop tables. Least privilege says the request-serving process should only have `SELECT/INSERT/UPDATE/DELETE`.

**4. Old code runs against the new schema.**
This one happens **wherever** the migration runs. During a rolling deploy, old tasks keep serving after V5 is applied. If V5 drops or renames a column the old code still reads, old tasks start failing with 500s mid-deploy. A separate migrate step doesn't fix this. Only the way you write migrations does (§7d).

### 7c. Solution: one migrate step, before the rollout

This is the P2 design (P2-T05, P2-T08): run Flyway **once**, as its own ECS task, and roll out only if it succeeds.

```
deploy.yml
  build → push image :<sha>
     │
     ▼
  run ECS task: same image, SPRING_PROFILES_ACTIVE=ecs,migrate
     │   spring.main.web-application-type=none
     │   Flyway migrates → context closes → process exits
     │
     ├─ exit ≠ 0 ──► stop. No service task has changed.
     │
     ▼ exit 0
  update the service → rolling deploy
     app tasks: spring.flyway.enabled=false, ddl-auto=validate
```

How each problem above is handled:

| Problem | Flyway at startup | Separate migrate task |
|---|---|---|
| 1. Startup blocked or killed by health checks | Yes | No: the migrate task has no web server and no health check, and app tasks don't migrate |
| 2. Failure during rollout | Crash loop mid-deploy | The deploy stops **before** any task changes |
| 3. App needs DDL rights | Yes | No: only the migrate task does (optionally a separate DB user just for it) |
| 4. Old code vs new schema | Must use expand/contract | **Still** must use expand/contract |

Details that make it work:

- **Same image, different profile.** The migrate task runs the exact code and migrations being deployed. No second artifact can drift out of sync.
- **It has to exit.** With `web-application-type=none`, Spring Boot runs Flyway during startup, has nothing left to do, and the JVM exits. Anything that keeps a non-daemon thread alive (a scheduler, an SQS listener, a WebSocket broker) also keeps the task running forever. `application-migrate.properties` must switch those off.
- **Why an ECS task and not the CI runner?** Aurora is in private subnets. A GitHub Actions runner can't reach it without a bastion or VPN. An ECS task runs inside the VPC with the same network and secrets as the app.
- **Where Flyway at startup is still right:** local, CI and Testcontainers ITs. There's one instance, no health check and nothing to roll back, so P1's "enabled everywhere" is the simple choice there.

### 7d. Solution for problem 4: expand/contract

The rule from `.claude/rules/flyway.md`: every migration must work with **both** the old and the new code, because both run during a deploy. A breaking change is split across releases.

Renaming `app_user.name` → `display_name`:

| Release | Migration | Code | Old code still works? |
|---|---|---|---|
| 1 (expand) | `ADD COLUMN display_name` (nullable), backfill from `name` | Writes both, reads `name` | ✅ Old code doesn't know the new column |
| 2 | — | Reads `display_name`, writes both | ✅ Release 1 code still reads `name`, which is still written |
| 3 (contract) | `SET NOT NULL` on `display_name`, `DROP COLUMN name` | Uses `display_name` only | ✅ Nothing running reads `name` anymore |

Doing it in one migration (`ALTER TABLE ... RENAME COLUMN`) is shorter, and it breaks every old task the moment it runs.

### 7e. Two different locks

Don't mix these up:

| Lock | Between | Purpose | Risk |
|---|---|---|---|
| Flyway advisory lock | migrating processes | only one migrates at a time | slow startups (§7b-1) |
| Table lock from DDL (`ALTER TABLE` takes `ACCESS EXCLUSIVE`) | the migration and **app traffic** | protects the table while its structure changes | stalls live requests |

The second one matters even with a separate migrate task. If `ALTER TABLE message ...` waits behind a long-running query, every new query on `message` queues behind the `ALTER`, and chat stalls. Two habits limit this:

- Start risky migrations with `SET lock_timeout = '5s';`. The migration fails fast and you retry, instead of freezing the table.
- Build indexes on large tables with `CREATE INDEX CONCURRENTLY`, in their own file with `-- flyway:executeInTransaction=false` at the top. That's the rule in `.claude/rules/flyway.md`. Don't rely on Flyway detecting it.

Batch backfills (`UPDATE ... WHERE id BETWEEN ...` in chunks) too, so that no single transaction holds row locks for minutes.

---

## Interview talking points

- **"Why not `ddl-auto=update` in production?"** It only adds, never records, and runs with the app's privileges at startup. You can't review it, reproduce it or roll it back.
- **"How do you introduce migrations to a system that already has a live schema?"** A baseline migration that equals the current schema, then `baseline-on-migrate` on the existing databases so V1 is recorded but not run. New databases run V1 for real.
- **"Migrations at app startup or as a separate step?"** Startup is fine for one instance. With many, use a separate step before the rollout (P2), and write migrations expand/contract so the old version keeps working mid-deploy.
- **"Why `validate` if Flyway already ran?"** Flyway proves the migrations ran. `validate` proves the **entities** agree with them. They catch different mistakes.

## Check yourself

1. You rename `UserEntity.name` to `displayName` and change the column. With `update`, what does the DB look like afterwards? What do you write with Flyway instead?
2. A teammate edits `V1__baseline.sql` after it's merged. What happens on their machine, on yours, and in CI?
3. Why does `INCREMENT BY 1` break this codebase specifically, and what annotation would let you use 1?
4. You add `Role.MODERATOR`. Every test passes and `validate` passes. What fails, and when?
5. Why is `baseline-on-migrate=true` the wrong fix for the local "non-empty schema" error?
6. Three `api` tasks start with Flyway enabled, and V5 builds an index on `message` that takes 4 minutes. Walk through what each task and the ALB do. Which of the §7b problems does a separate migrate task fix, and which one does it not?
