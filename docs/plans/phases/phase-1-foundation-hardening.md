# Phase 1 — Foundation hardening

**Weeks:** 1–2 · **Depends on:** P0 · **ADRs:** 0009, 0011

## Goal
Make the existing backend safe and deployable before building on it:
- A migration-managed schema.
- The open security holes closed.
- Health endpoints.
- Integration tests on a real Postgres.
- A CI that actually fails when something is wrong.

## Why it matters (interview angle)
"What did you fix before adding features?" is a senior-level question. Schema migrations, authz-vs-authn and a CI that can't lie are exactly the kind of foundational judgement interviewers look for.

## Prerequisites
- P0 done.
- Docker running locally.

## Current state (verified 2026-09-29)
Status of the security audit in [`learning/README.md`](../../learning/README.md) "Fix these first":

| # | Issue | Status |
|---|---|---|
| 1 | `UserController` returns the raw `User` domain object (password hash, tokenVersion) | **Open** |
| 2 | `PasswordIncorrectException` puts the submitted password in `details` | **Open** |
| 3 | `tokenVersion >=` comparison | Fixed (`Objects.equals`) |
| 4 | `POST /channels/{id}/members`: no authorization | **Open** |
| 5 | `PATCH /channels/{id}`: no authorization | **Open** |
| 6 | `POST /auth/password` returns `null`; `UserAdapter.updatePassword` returns `false` | **Open** |
| 7 | Refresh cookie `path=/api/v1/auth/refresh` doesn't match `/refresh_token` or `/logout` | **Open** |
| 8 | No Flyway migrations; `ecs` can't start on a fresh DB | **Open** |
| + | `/api/v1/channels` is `permitAll` → NPE on `auth.getName()` without a token | **Open** |
| + | `verifyRefresh` returns `null` → NPE in `refresh` | **Open** |

## Scope
- **In:**
  - The items above.
  - Flyway baseline.
  - pgvector image.
  - Actuator.
  - Testcontainers.
  - ArchUnit.
  - CI fixes.
  - Structured logging.
- **Out:**
  - New features.
  - Channel roles beyond what #4 and #5 need (P3 completes role management).

## Design notes
- **Flyway baseline:** generate DDL from the current entities once (`spring.jpa.properties.jakarta.persistence.schema-generation.scripts.*` or `pg_dump --schema-only` from a local DB created by `ddl-auto=create`), review it by hand, and commit it as `V1__baseline.sql`. Then set `ddl-auto=validate` and `spring.flyway.enabled=true` in **every** profile, including `local`.
- Do **not** change id types in V1; the BIGINT migration is P3-T01, as its own migration.
- **Authorization for #4 and #5:** only `CREATOR`/`LEADER` members may update a channel or add members. The check lives in `ChannelService` (the application layer), which throws a domain `ForbiddenException` mapped to 403. The controller passes the caller's id.
- **Health:** `management.endpoint.health.probes.enabled=true`, and expose only `health` and `info` publicly. The ALB uses `/actuator/health/readiness`.

## Tasks
- [ ] **P1-T01** Flyway `V1__baseline.sql` from the current entities. Enable Flyway and `ddl-auto=validate` in all profiles. Fix CLAUDE.md's profile table if it still differs
- [ ] **P1-T02** Local DB → `pgvector/pgvector:pg16` in `docker/local/compose.yaml`. Move the credentials to an `.env` file referenced by compose (not committed). Update `application-local.properties`
- [ ] **P1-T03** `V2__message_channel_seq_index.sql`: index `(channel_id, id DESC)` (from improvements item #3)
- [x] **P1-T04** #1: `UserController` returns a `UserProfileResponse` DTO via MapStruct. Add an IT asserting that no `password`/`tokenVersion` appears in the JSON
- [ ] **P1-T05** #2: remove the password from exception details. Add a test that the error body doesn't echo request fields
- [ ] **P1-T06** #4, #5: pass the caller's id into `updateChannel`/`addMember`. Check the role in `ChannelService`; 403 otherwise. Add unit tests for member, creator, leader and non-member
- [ ] **P1-T07** #6: implement change-password end to end (verify the old password, hash the new one, bump `tokenVersion`, return 204), or remove the endpoint until P3. Decide and document
- [ ] **P1-T08** #7: cookie `Path=/api/v1/auth`, `HttpOnly; Secure; SameSite=Strict`. Fix the `verifyRefresh` null → throw `InvalidRefreshTokenException` → 401
- [ ] **P1-T09** Remove `/api/v1/channels` from `permitAll`. Add a 401 test without a token
- [ ] **P1-T10** Add `spring-boot-starter-actuator`. Enable the liveness and readiness probes, plus graceful shutdown (`server.shutdown=graceful`, `spring.lifecycle.timeout-per-shutdown-phase=30s`)
- [ ] **P1-T11** Testcontainers: `AbstractIntegrationTest` with `@ServiceConnection` Postgres (`pgvector/pgvector:pg16`) and a reusable container. Convert `DevCoolApplicationTests` into a real context + Flyway smoke IT
- [ ] **P1-T12** First ITs: `MessageRepository` cursor query, `ChannelController` authz (403/401), refresh-cookie flow with `MockMvc`
- [ ] **P1-T13** ArchUnit rules:
  - `domain..` must not depend on `org.springframework..`, `jakarta.persistence..` or `software.amazon..`.
  - `application..` depends on `domain..` ports only (no `adapters..`, no `*Repository`).
- [ ] **P1-T14** CI:
  - Run ITs (`./mvnw -B verify -Dit`).
  - Drop `|| true` from static analysis. Fix or suppress the existing findings explicitly first.
  - Cache Testcontainers images.
- [ ] **P1-T15** Structured JSON logging (`logging.structured.format.console=ecs` in `ecs`; human-readable locally). Log `userId`/`connectionId` via MDC in the WS handler
- [ ] **P1-T16** Enable virtual threads (`spring.threads.virtual.enabled=true`). Check that no `synchronized` block wraps blocking I/O in the WS send path
- [ ] **P1-T17** Update `docs/learning/README.md` "Fix these first" with the status of each item and link the PRs

## Files touched
- `src/main/resources/db/migration/*`
- `application*.properties`
- `docker/local/compose.yaml`
- `pom.xml` (actuator, testcontainers, archunit)
- `adapters/in/web/config/SecurityConfig.java`
- `adapters/in/web/controller/{User,Channel,Auth}Controller.java`
- `application/service/channel/ChannelService.java`
- `domain/auth/exception/PasswordIncorrectException.java`
- `.github/workflows/ci.yml`
- `src/test/java/**`

## Test plan
- **Unit:** role checks in `ChannelService`; exception details.
- **IT (Testcontainers):** Flyway applies cleanly on an empty DB and `validate` passes. The authz matrix on channel endpoints. User profile JSON shape. Refresh cookie round-trip.
- **Arch:** ArchUnit suite runs in `./mvnw test`.

## Definition of Done
- `docker compose down -v && ./mvnw spring-boot:run -Dspring-boot.run.arguments=--spring.profiles.active=local` builds the schema from Flyway alone.
- CI runs unit + IT + static analysis, and fails on any of them.
- Every "Open" row above is Fixed, with a test.

## Interview talking points
- Why `ddl-auto` in production is dangerous, and how baseline migrations work on an existing schema.
- "Authenticated isn't authorized": the channel endpoints bug and where the check belongs (service, not controller).
- Why a CI step with `|| true` is worse than no step.
- Testcontainers vs H2: test against what you run.

## Risks
- The generated baseline may differ from the entities in details (enum columns, lengths). Review by hand and run `validate` in an IT.
- Static analysis may produce many findings. Time-box it: fix real bugs, suppress the rest with justification.
